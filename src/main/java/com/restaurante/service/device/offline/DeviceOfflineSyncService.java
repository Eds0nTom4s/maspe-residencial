package com.restaurante.service.device.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.restaurante.config.DeviceOfflineSyncProperties;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.dto.request.DeviceOfflineCommandRequest;
import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.DeviceOfflineCapabilitiesResponse;
import com.restaurante.dto.response.DeviceOfflineCommandResultResponse;
import com.restaurante.dto.response.DeviceOfflineSyncBatchResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineConflictCode;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceOfflineSyncService {

    private final DeviceOfflineSyncProperties properties;
    private final DeviceOfflineCommandRepository repository;
    private final DeviceOfflineCommandProcessor processor;
    private final ObjectMapper objectMapper;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final OperationalEventLogService operationalEventLogService;

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof DevicePrincipal dp)) {
            throw new DeviceUnauthorizedException("Dispositivo não autenticado.");
        }
        return (DevicePrincipal) auth.getPrincipal();
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_OFFLINE_CAPABILITY_REQUIRED,
                    "Dispositivo sem permissão para operação offline (" + capability.name() + ").",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    Map.of("required", capability.name()));
        }
    }

    @Transactional(readOnly = true)
    public DeviceOfflineCapabilitiesResponse capabilities() {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceOfflineCapabilitiesResponse resp = new DeviceOfflineCapabilitiesResponse();
        resp.setOfflineEnabled(properties.isEnabled());
        resp.setAllowedCommandTypes(properties.getAllowedCommandTypes());
        resp.setMaxBatchSize(properties.getMaxBatchSize());
        resp.setMaxOfflineAgeMinutes(properties.getMaxOfflineAgeMinutes());
        resp.setServerTime(Instant.now());
        resp.setDeviceId(device.dispositivoId());
        resp.setUnidadeId(device.unidadeAtendimentoId());
        resp.setTenantId(device.tenantId());
        return resp;
    }

    @Transactional(readOnly = true)
    public DeviceOfflineCommandResultResponse getCommandByClientRequestId(String clientRequestId) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceOfflineCommand cmd = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), clientRequestId)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Comando não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));
        return toResult(cmd, cmd.getStatus(), safeReadTree(cmd.getResultJson()));
    }

    @Transactional
    public DeviceOfflineSyncBatchResponse syncBatch(DeviceOfflineSyncBatchRequest request, String ip, String userAgent) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.OFFLINE_SYNC);

        if (!properties.isEnabled()) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_SYNC_DISABLED,
                    "Offline sync desativado no servidor.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request == null || request.getCommands() == null || request.getCommands().isEmpty()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                    "commands é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request.getCommands().size() > properties.getMaxBatchSize()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_BATCH_TOO_LARGE,
                    "Batch excede o tamanho máximo permitido.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    Map.of("maxBatchSize", properties.getMaxBatchSize()));
        }

        String syncId = UUID.randomUUID().toString();
        Instant receivedAt = Instant.now();

        Tenant tenant = tenantRepository.findById(device.tenantId()).orElseThrow(() -> new DeviceApiException(
                HttpStatus.NOT_FOUND,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                "Recurso não encontrado.",
                false,
                DeviceErrorResponse.DeviceRecoveryAction.NONE,
                null
        ));
        Instituicao inst = device.instituicaoId() != null ? instituicaoRepository.findById(device.instituicaoId()).orElse(null) : null;
        UnidadeAtendimento unidade = device.unidadeAtendimentoId() != null ? unidadeAtendimentoRepository.findById(device.unidadeAtendimentoId()).orElse(null) : null;

        operationalEventLogService.logPublicEvent(
                tenant,
                inst,
                unidade,
                null,
                null,
                OperationalEventType.DEVICE_OFFLINE_SYNC_RECEIVED,
                OperationalEntityType.DEVICE_OFFLINE_SYNC,
                null,
                OperationalOrigem.DEVICE_POS,
                "Device offline sync received",
                Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "syncId", syncId, "total", request.getCommands().size()),
                ip,
                userAgent
        );

        List<DeviceOfflineCommandRequest> ordered = orderCommands(request.getCommands());

        DeviceOfflineSyncBatchResponse resp = new DeviceOfflineSyncBatchResponse();
        resp.setSyncId(syncId);
        resp.setReceivedAt(receivedAt);
        resp.setTotal(ordered.size());

        int applied = 0, duplicates = 0, rejected = 0, conflicts = 0, failed = 0;

        List<DeviceOfflineCommandResultResponse> results = new java.util.ArrayList<>(ordered.size());
        for (DeviceOfflineCommandRequest cmdReq : ordered) {
            DeviceOfflineCommandResultResponse r = processOne(device, tenant, inst, unidade, cmdReq, ip, userAgent);
            results.add(r);
            if (r.getStatus() == DeviceOfflineCommandStatus.APPLIED) applied++;
            else if (r.getStatus() == DeviceOfflineCommandStatus.DUPLICATE) duplicates++;
            else if (r.getStatus() == DeviceOfflineCommandStatus.REJECTED) rejected++;
            else if (r.getStatus() == DeviceOfflineCommandStatus.CONFLICT) conflicts++;
            else if (r.getStatus() == DeviceOfflineCommandStatus.FAILED) failed++;
        }

        resp.setApplied(applied);
        resp.setDuplicates(duplicates);
        resp.setRejected(rejected);
        resp.setConflicts(conflicts);
        resp.setFailed(failed);
        resp.setResults(results);

        operationalEventLogService.logPublicEvent(
                tenant,
                inst,
                unidade,
                null,
                null,
                OperationalEventType.DEVICE_OFFLINE_SYNC_COMPLETED,
                OperationalEntityType.DEVICE_OFFLINE_SYNC,
                null,
                OperationalOrigem.DEVICE_POS,
                "Device offline sync completed",
                Map.of(
                        "tenantId", device.tenantId(),
                        "deviceId", device.dispositivoId(),
                        "syncId", syncId,
                        "total", ordered.size(),
                        "applied", applied,
                        "duplicates", duplicates,
                        "rejected", rejected,
                        "conflicts", conflicts,
                        "failed", failed
                ),
                ip,
                userAgent
        );

        return resp;
    }

    private List<DeviceOfflineCommandRequest> orderCommands(List<DeviceOfflineCommandRequest> commands) {
        return commands.stream()
                .sorted(Comparator
                        .comparing(DeviceOfflineCommandRequest::getLocalSequence, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(DeviceOfflineCommandRequest::getLocalCreatedAt, Comparator.nullsLast(Instant::compareTo)))
                .toList();
    }

    private DeviceOfflineCommandResultResponse processOne(DevicePrincipal device,
                                                         Tenant tenant,
                                                         Instituicao inst,
                                                         UnidadeAtendimento unidade,
                                                         DeviceOfflineCommandRequest req,
                                                         String ip,
                                                         String userAgent) {
        if (req == null || req.getClientRequestId() == null || req.getClientRequestId().isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                    "clientRequestId é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }

        DeviceOfflineCommandType type = req.getCommandType();
        if (type == null || properties.getAllowedCommandTypes() == null || !properties.getAllowedCommandTypes().contains(type)) {
            // não persiste comando desconhecido/fora de allowlist para evitar lixo no banco
            DeviceOfflineCommandResultResponse r = new DeviceOfflineCommandResultResponse();
            r.setClientRequestId(req.getClientRequestId());
            r.setCommandType(type);
            r.setStatus(DeviceOfflineCommandStatus.REJECTED);
            r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_COMMAND_TYPE_NOT_ALLOWED.name());
            r.setErrorMessage("Tipo de comando não permitido offline.");
            r.setConflictCode(DeviceOfflineConflictCode.COMMAND_TYPE_NOT_ALLOWED_OFFLINE.name());

            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    OperationalEventType.DEVICE_OFFLINE_COMMAND_REJECTED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    null,
                    OperationalOrigem.DEVICE_POS,
                    "Offline command rejected",
                    Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "clientRequestId", req.getClientRequestId(), "commandType", String.valueOf(type)),
                    ip, userAgent
            );
            return r;
        }

        if (req.getLocalCreatedAt() != null) {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.getMaxOfflineAgeMinutes()));
            if (req.getLocalCreatedAt().isBefore(cutoff)) {
                DeviceOfflineCommandResultResponse r = new DeviceOfflineCommandResultResponse();
                r.setClientRequestId(req.getClientRequestId());
                r.setCommandType(type);
                r.setStatus(DeviceOfflineCommandStatus.REJECTED);
                r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_COMMAND_TOO_OLD.name());
                r.setErrorMessage("Comando offline antigo demais; reenviar após refresh.");
                r.setConflictCode(null);
                return r;
            }
        }

        String payloadJson = writeCanonicalJson(req.getPayload());
        String payloadHash = sha256Hex(payloadJson);

        DeviceOfflineCommand existing = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), req.getClientRequestId())
                .orElse(null);
        if (existing != null) {
            if (!payloadHash.equals(existing.getPayloadHash())) {
                DeviceOfflineCommandResultResponse r = toResult(existing, DeviceOfflineCommandStatus.CONFLICT, safeReadTree(existing.getResultJson()));
                r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.IDEMPOTENCY_CONFLICT.name());
                r.setErrorMessage("Conflito de idempotência: mesmo clientRequestId com payload diferente.");
                r.setConflictCode(DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name());
                return r;
            }
            DeviceOfflineCommandResultResponse r = toResult(existing, DeviceOfflineCommandStatus.DUPLICATE, safeReadTree(existing.getResultJson()));
            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    OperationalEventType.DEVICE_OFFLINE_COMMAND_DUPLICATE,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    existing.getId(),
                    OperationalOrigem.DEVICE_POS,
                    "Offline command duplicate",
                    Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "commandId", existing.getId(), "clientRequestId", existing.getClientRequestId(), "commandType", existing.getCommandType().name()),
                    ip, userAgent
            );
            return r;
        }

        DeviceOfflineCommand cmd = new DeviceOfflineCommand();
        cmd.setTenant(tenant);
        cmd.setUnidadeAtendimento(unidade);
        DispositivoOperacional dispRef = dispositivoOperacionalRepository.getReferenceById(device.dispositivoId());
        cmd.setDispositivoOperacional(dispRef);
        cmd.setClientRequestId(req.getClientRequestId().trim());
        cmd.setCommandType(type);
        cmd.setCommandVersion(req.getCommandVersion() != null ? req.getCommandVersion().trim() : "1");
        cmd.setStatus(DeviceOfflineCommandStatus.RECEIVED);
        cmd.setLocalCreatedAt(req.getLocalCreatedAt());
        cmd.setLocalSequence(req.getLocalSequence());
        cmd.setPayloadHash(payloadHash);
        cmd.setPayloadJson(payloadJson);
        cmd.setIdempotencyScope("TENANT_DEVICE_CLIENT_REQUEST_ID");

        try {
            cmd = repository.saveAndFlush(cmd);
        } catch (DataIntegrityViolationException ex) {
            // corrida: recarrega como existente
            DeviceOfflineCommand raced = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), req.getClientRequestId()).orElseThrow(() -> ex);
            if (!payloadHash.equals(raced.getPayloadHash())) {
                DeviceOfflineCommandResultResponse r = toResult(raced, DeviceOfflineCommandStatus.CONFLICT, safeReadTree(raced.getResultJson()));
                r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.IDEMPOTENCY_CONFLICT.name());
                r.setErrorMessage("Conflito de idempotência: mesmo clientRequestId com payload diferente.");
                r.setConflictCode(DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name());
                return r;
            }
            return toResult(raced, DeviceOfflineCommandStatus.DUPLICATE, safeReadTree(raced.getResultJson()));
        }

        cmd.setStatus(DeviceOfflineCommandStatus.PROCESSING);
        repository.save(cmd);

        // capabilities por tipo de comando (offline exige opt-in explícito)
        switch (type) {
            case CREATE_PEDIDO_POS -> requireCapability(device, DeviceCapability.OFFLINE_CREATE_ORDER);
            case CREATE_ORDEM_PAGAMENTO_MANUAL -> requireCapability(device, DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER);
            case CONFIRM_MANUAL_PAYMENT -> requireCapability(device, DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT);
            case REGISTER_LOCAL_ACTIVITY -> { /* sem extra */ }
        }

        String derivedIdempotencyKey = "offline:" + device.dispositivoId() + ":" + req.getClientRequestId().trim();
        try {
            var processed = processor.process(device, type, req.getClientRequestId().trim(), req.getPayload(), derivedIdempotencyKey, ip, userAgent);

            cmd.setStatus(DeviceOfflineCommandStatus.APPLIED);
            cmd.setProcessedAt(Instant.now());
            cmd.setCreatedEntityType(processed.createdEntityType());
            cmd.setCreatedEntityId(processed.createdEntityId());
            cmd.setResultJson(writeCanonicalJson(processed.resultJson()));
            cmd.setErrorCode(null);
            cmd.setErrorMessage(null);
            cmd.setConflictCode(null);
            repository.save(cmd);

            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    OperationalEventType.DEVICE_OFFLINE_COMMAND_APPLIED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    cmd.getId(),
                    OperationalOrigem.DEVICE_POS,
                    "Offline command applied",
                    Map.of(
                            "tenantId", device.tenantId(),
                            "deviceId", device.dispositivoId(),
                            "commandId", cmd.getId(),
                            "clientRequestId", cmd.getClientRequestId(),
                            "commandType", cmd.getCommandType().name(),
                            "createdEntityType", cmd.getCreatedEntityType(),
                            "createdEntityId", cmd.getCreatedEntityId()
                    ),
                    ip, userAgent
            );

            return toResult(cmd, DeviceOfflineCommandStatus.APPLIED, processed.resultJson());
        } catch (DeviceApiException ex) {
            DeviceOfflineCommandStatus st = ex.getStatus() == HttpStatus.CONFLICT ? DeviceOfflineCommandStatus.CONFLICT : DeviceOfflineCommandStatus.REJECTED;
            cmd.setStatus(st);
            cmd.setFailedAt(Instant.now());
            cmd.setErrorCode(ex.getCode().name());
            cmd.setErrorMessage(ex.getMessage());
            if (st == DeviceOfflineCommandStatus.CONFLICT) {
                cmd.setConflictCode(mapConflictCode(ex.getCode()));
            }
            repository.save(cmd);

            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    st == DeviceOfflineCommandStatus.CONFLICT ? OperationalEventType.DEVICE_OFFLINE_COMMAND_CONFLICT : OperationalEventType.DEVICE_OFFLINE_COMMAND_REJECTED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    cmd.getId(),
                    OperationalOrigem.DEVICE_POS,
                    "Offline command not applied",
                    Map.of(
                            "tenantId", device.tenantId(),
                            "deviceId", device.dispositivoId(),
                            "commandId", cmd.getId(),
                            "clientRequestId", cmd.getClientRequestId(),
                            "commandType", cmd.getCommandType().name(),
                            "errorCode", ex.getCode().name()
                    ),
                    ip, userAgent
            );

            DeviceOfflineCommandResultResponse r = toResult(cmd, st, null);
            r.setErrorCode(ex.getCode().name());
            r.setErrorMessage(ex.getMessage());
            r.setConflictCode(cmd.getConflictCode());
            return r;
        } catch (Exception ex) {
            cmd.setStatus(DeviceOfflineCommandStatus.FAILED);
            cmd.setFailedAt(Instant.now());
            cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.DEVICE_INTERNAL_ERROR.name());
            cmd.setErrorMessage("Falha interna ao processar comando offline.");
            repository.save(cmd);

            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    OperationalEventType.DEVICE_OFFLINE_COMMAND_FAILED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    cmd.getId(),
                    OperationalOrigem.DEVICE_POS,
                    "Offline command failed",
                    Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "commandId", cmd.getId(), "clientRequestId", cmd.getClientRequestId(), "commandType", cmd.getCommandType().name()),
                    ip, userAgent
            );

            DeviceOfflineCommandResultResponse r = toResult(cmd, DeviceOfflineCommandStatus.FAILED, null);
            r.setErrorCode(cmd.getErrorCode());
            r.setErrorMessage(cmd.getErrorMessage());
            return r;
        }
    }

    private String mapConflictCode(DeviceErrorResponse.DeviceErrorCode code) {
        if (code == null) return null;
        return switch (code) {
            case PRICE_CHANGED -> DeviceOfflineConflictCode.PRICE_CHANGED.name();
            case PRODUCT_INACTIVE -> DeviceOfflineConflictCode.PRODUCT_INACTIVE.name();
            case SESSION_CLOSED -> DeviceOfflineConflictCode.SESSION_CLOSED.name();
            case PAYMENT_METHOD_NOT_ALLOWED -> DeviceOfflineConflictCode.PAYMENT_METHOD_NOT_ALLOWED.name();
            case TURNO_NOT_OPEN -> DeviceOfflineConflictCode.TURNO_NOT_OPEN.name();
            case IDEMPOTENCY_CONFLICT -> DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name();
            default -> DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name();
        };
    }

    private DeviceOfflineCommandResultResponse toResult(DeviceOfflineCommand cmd, DeviceOfflineCommandStatus status, JsonNode result) {
        DeviceOfflineCommandResultResponse r = new DeviceOfflineCommandResultResponse();
        r.setClientRequestId(cmd.getClientRequestId());
        r.setCommandType(cmd.getCommandType());
        r.setStatus(status);
        r.setCreatedEntityType(cmd.getCreatedEntityType());
        r.setCreatedEntityId(cmd.getCreatedEntityId());
        r.setResult(result);
        r.setErrorCode(cmd.getErrorCode());
        r.setErrorMessage(cmd.getErrorMessage());
        r.setConflictCode(cmd.getConflictCode());
        return r;
    }

    private String writeCanonicalJson(Object value) {
        try {
            ObjectMapper mapper = objectMapper.copy();
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private JsonNode safeReadTree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(value != null ? value.hashCode() : 0);
        }
    }
}
