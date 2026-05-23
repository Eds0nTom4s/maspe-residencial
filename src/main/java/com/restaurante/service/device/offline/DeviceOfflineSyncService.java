package com.restaurante.service.device.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        resp.setMaxBatchPayloadBytes(properties.getMaxBatchPayloadBytes());
        resp.setMaxCommandPayloadBytes(properties.getMaxCommandPayloadBytes());
        resp.setMaxPedidoItems(properties.getMaxPedidoItems());
        resp.setMaxLocalRefDepth(properties.getMaxLocalRefDepth());
        resp.setAllowForwardLocalRefs(properties.isAllowForwardLocalRefs());
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
        JsonNode stored = safeReadTree(cmd.getResultJson());
        DeviceOfflineCommandResultResponse r = toResult(cmd, cmd.getStatus(), extractStoredResult(stored));
        r.setResolvedRefs(extractStoredResolvedRefs(stored));
        return r;
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
        try {
            validateBatchIdsAndLimits(ordered);
        } catch (DeviceApiException ex) {
            OperationalEventType evt = switch (ex.getCode()) {
                case OFFLINE_BATCH_PAYLOAD_TOO_LARGE, OFFLINE_COMMAND_PAYLOAD_TOO_LARGE, OFFLINE_TOO_MANY_ITEMS, OFFLINE_BATCH_TOO_LARGE -> OperationalEventType.DEVICE_OFFLINE_PAYLOAD_LIMIT_REJECTED;
                case OFFLINE_LOCALREF_CIRCULAR_DEPENDENCY, OFFLINE_LOCALREF_FORWARD_REFERENCE_NOT_ALLOWED, OFFLINE_DEPENDENCY_DEPTH_EXCEEDED -> OperationalEventType.DEVICE_OFFLINE_DEPENDENCY_FAILED;
                default -> OperationalEventType.DEVICE_OFFLINE_COMMAND_REJECTED;
            };
            operationalEventLogService.logPublicEvent(
                    tenant, inst, unidade, null, null,
                    evt,
                    OperationalEntityType.DEVICE_OFFLINE_SYNC,
                    null,
                    OperationalOrigem.DEVICE_POS,
                    "Offline sync batch rejected",
                    Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "syncId", syncId, "errorCode", ex.getCode().name()),
                    ip, userAgent
            );
            throw ex;
        }

        DeviceOfflineSyncBatchResponse resp = new DeviceOfflineSyncBatchResponse();
        resp.setSyncId(syncId);
        resp.setReceivedAt(receivedAt);
        resp.setTotal(ordered.size());

        int applied = 0, duplicates = 0, rejected = 0, conflicts = 0, failed = 0;

        List<DeviceOfflineCommandResultResponse> results = new java.util.ArrayList<>(ordered.size());
        Map<String, ResolvedEntityRef> resolved = new HashMap<>();
        Map<String, DeviceOfflineCommandStatus> outcomeByClientRequestId = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            DeviceOfflineCommandRequest cmdReq = ordered.get(i);
            DeviceOfflineCommandResultResponse r = processOne(device, tenant, inst, unidade, cmdReq, i, resolved, outcomeByClientRequestId, ip, userAgent);
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

    private record ResolvedEntityRef(String entityType, Long entityId, String sourceClientRequestId) {}

    private void validateBatchIdsAndLimits(List<DeviceOfflineCommandRequest> ordered) {
        Set<String> ids = new HashSet<>();
        Set<String> localRefs = new HashSet<>();
        int totalBytes = 0;

        for (DeviceOfflineCommandRequest req : ordered) {
            if (req == null || req.getClientRequestId() == null || req.getClientRequestId().isBlank()) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                        "clientRequestId é obrigatório.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                        null);
            }
            String id = req.getClientRequestId().trim();
            if (!ids.add(id)) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                        "clientRequestId duplicado no batch: " + id,
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        Map.of("clientRequestId", id));
            }
            String lr = (req.getLocalRef() == null || req.getLocalRef().isBlank()) ? id : req.getLocalRef().trim();
            if (!localRefs.add(lr)) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                        "localRef duplicado no batch: " + lr,
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        Map.of("localRef", lr));
            }

            String payloadJson = writeCanonicalJson(req.getPayload());
            int bytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > properties.getMaxCommandPayloadBytes()) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_COMMAND_PAYLOAD_TOO_LARGE,
                        "Payload do comando excede o limite.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        Map.of("maxCommandPayloadBytes", properties.getMaxCommandPayloadBytes(), "payloadBytes", bytes));
            }
            totalBytes += bytes;
            if (totalBytes > properties.getMaxBatchPayloadBytes()) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_BATCH_PAYLOAD_TOO_LARGE,
                        "Payload total do batch excede o limite.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        Map.of("maxBatchPayloadBytes", properties.getMaxBatchPayloadBytes(), "payloadBytes", totalBytes));
            }

            if (req.getCommandType() == DeviceOfflineCommandType.CREATE_PEDIDO_POS) {
                JsonNode itens = req.getPayload() != null ? req.getPayload().get("itens") : null;
                if (itens != null && itens.isArray() && itens.size() > properties.getMaxPedidoItems()) {
                    throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                            DeviceErrorResponse.DeviceErrorCode.OFFLINE_TOO_MANY_ITEMS,
                            "Número máximo de itens excedido para pedido offline.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            Map.of("maxPedidoItems", properties.getMaxPedidoItems(), "items", itens.size()));
                }
            }
        }

        // valida dependências (ciclo, profundidade, forward refs) apenas entre comandos do batch
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) indexById.put(ordered.get(i).getClientRequestId().trim(), i);

        Map<String, List<String>> depsById = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            DeviceOfflineCommandRequest r = ordered.get(i);
            List<String> deps = inferDependsOn(r);
            depsById.put(r.getClientRequestId().trim(), deps);

            for (String dep : deps) {
                Integer depIdx = indexById.get(dep);
                if (depIdx != null && !properties.isAllowForwardLocalRefs() && depIdx > i) {
                    throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                            DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_FORWARD_REFERENCE_NOT_ALLOWED,
                            "Forward localRef não permitido (dependência futura).",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            Map.of("clientRequestId", r.getClientRequestId(), "dependsOn", dep));
                }
            }
        }

        detectCircularDependency(depsById);
        int maxDepth = computeMaxDependencyDepth(depsById);
        if (maxDepth > properties.getMaxLocalRefDepth()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_DEPENDENCY_DEPTH_EXCEEDED,
                    "Profundidade máxima de dependência excedida.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    Map.of("maxLocalRefDepth", properties.getMaxLocalRefDepth(), "maxDepth", maxDepth));
        }
    }

    private List<String> inferDependsOn(DeviceOfflineCommandRequest req) {
        if (req == null) return List.of();
        if (req.getDependsOn() != null && !req.getDependsOn().isEmpty()) {
            return req.getDependsOn().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        }
        JsonNode payload = req.getPayload();
        if (payload == null || req.getCommandType() == null) return List.of();
        return switch (req.getCommandType()) {
            case CREATE_ORDEM_PAGAMENTO_MANUAL -> {
                JsonNode v = payload.get("pedidoClientRequestId");
                yield v != null && !v.isNull() && v.asText() != null && !v.asText().isBlank() ? List.of(v.asText().trim()) : List.of();
            }
            case CONFIRM_MANUAL_PAYMENT -> {
                JsonNode v = payload.get("ordemPagamentoClientRequestId");
                yield v != null && !v.isNull() && v.asText() != null && !v.asText().isBlank() ? List.of(v.asText().trim()) : List.of();
            }
            default -> List.of();
        };
    }

    private void detectCircularDependency(Map<String, List<String>> depsById) {
        Map<String, Integer> color = new HashMap<>();
        for (String node : depsById.keySet()) color.put(node, 0);
        for (String node : depsById.keySet()) {
            if (color.get(node) == 0) dfsCycle(node, depsById, color);
        }
    }

    private void dfsCycle(String node, Map<String, List<String>> depsById, Map<String, Integer> color) {
        color.put(node, 1);
        for (String dep : depsById.getOrDefault(node, List.of())) {
            if (!depsById.containsKey(dep)) continue;
            int c = color.getOrDefault(dep, 0);
            if (c == 1) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_CIRCULAR_DEPENDENCY,
                        "Dependência circular detectada no batch.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        Map.of("from", node, "to", dep));
            }
            if (c == 0) dfsCycle(dep, depsById, color);
        }
        color.put(node, 2);
    }

    private int computeMaxDependencyDepth(Map<String, List<String>> depsById) {
        Map<String, Integer> memo = new HashMap<>();
        int max = 0;
        for (String node : depsById.keySet()) max = Math.max(max, depth(node, depsById, memo));
        return max;
    }

    private int depth(String node, Map<String, List<String>> depsById, Map<String, Integer> memo) {
        if (memo.containsKey(node)) return memo.get(node);
        int maxChild = 0;
        for (String dep : depsById.getOrDefault(node, List.of())) {
            if (!depsById.containsKey(dep)) continue;
            maxChild = Math.max(maxChild, depth(dep, depsById, memo));
        }
        int d = depsById.getOrDefault(node, List.of()).isEmpty() ? 0 : (maxChild + 1);
        memo.put(node, d);
        return d;
    }

    private DeviceOfflineCommandResultResponse processOne(DevicePrincipal device,
                                                         Tenant tenant,
                                                         Instituicao inst,
                                                         UnidadeAtendimento unidade,
                                                         DeviceOfflineCommandRequest req,
                                                         int commandIndex,
                                                         Map<String, ResolvedEntityRef> resolved,
                                                         Map<String, DeviceOfflineCommandStatus> outcomeByClientRequestId,
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

        String clientRequestId = req.getClientRequestId().trim();
        String localRef = (req.getLocalRef() == null || req.getLocalRef().isBlank()) ? clientRequestId : req.getLocalRef().trim();

        String payloadJson = writeCanonicalJson(req.getPayload());
        String payloadHash = sha256Hex(payloadJson);
        int payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;

        DeviceOfflineCommand existing = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), clientRequestId)
                .orElse(null);
        if (existing != null) {
            if (!payloadHash.equals(existing.getPayloadHash())) {
                JsonNode stored = safeReadTree(existing.getResultJson());
                DeviceOfflineCommandResultResponse r = toResult(existing, DeviceOfflineCommandStatus.CONFLICT, extractStoredResult(stored));
                r.setResolvedRefs(extractStoredResolvedRefs(stored));
                r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.IDEMPOTENCY_CONFLICT.name());
                r.setErrorMessage("Conflito de idempotência: mesmo clientRequestId com payload diferente.");
                r.setConflictCode(DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name());
                return r;
            }
            JsonNode stored = safeReadTree(existing.getResultJson());
            DeviceOfflineCommandResultResponse r = toResult(existing, DeviceOfflineCommandStatus.DUPLICATE, extractStoredResult(stored));
            r.setResolvedRefs(extractStoredResolvedRefs(stored));
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
            if (existing.getCreatedEntityType() != null && existing.getCreatedEntityId() != null) {
                ResolvedEntityRef ref = new ResolvedEntityRef(existing.getCreatedEntityType(), existing.getCreatedEntityId(), existing.getClientRequestId());
                resolved.put(localRef, ref);
                resolved.put(existing.getClientRequestId(), ref);
            }
            outcomeByClientRequestId.put(existing.getClientRequestId(), DeviceOfflineCommandStatus.DUPLICATE);
            return r;
        }

        DeviceOfflineCommand cmd = new DeviceOfflineCommand();
        cmd.setTenant(tenant);
        cmd.setUnidadeAtendimento(unidade);
        DispositivoOperacional dispRef = dispositivoOperacionalRepository.getReferenceById(device.dispositivoId());
        cmd.setDispositivoOperacional(dispRef);
        cmd.setClientRequestId(clientRequestId);
        cmd.setCommandType(type);
        cmd.setCommandVersion(req.getCommandVersion() != null ? req.getCommandVersion().trim() : "1");
        cmd.setStatus(DeviceOfflineCommandStatus.RECEIVED);
        cmd.setLocalCreatedAt(req.getLocalCreatedAt());
        cmd.setLocalSequence(req.getLocalSequence());
        cmd.setPayloadHash(payloadHash);
        cmd.setPayloadJson(payloadJson);
        cmd.setPayloadSizeBytes(payloadBytes);
        cmd.setCommandIndex(commandIndex);
        List<String> deps = inferDependsOn(req);
        cmd.setDependsOnClientRequestId(deps != null && !deps.isEmpty() ? deps.get(0) : null);
        cmd.setDependencyStatus(deps != null && !deps.isEmpty() ? "WAITING" : "NONE");
        cmd.setIdempotencyScope("TENANT_DEVICE_CLIENT_REQUEST_ID");

        try {
            cmd = repository.saveAndFlush(cmd);
        } catch (DataIntegrityViolationException ex) {
            // corrida: recarrega como existente
            DeviceOfflineCommand raced = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), clientRequestId).orElseThrow(() -> ex);
            if (!payloadHash.equals(raced.getPayloadHash())) {
                JsonNode stored = safeReadTree(raced.getResultJson());
                DeviceOfflineCommandResultResponse r = toResult(raced, DeviceOfflineCommandStatus.CONFLICT, extractStoredResult(stored));
                r.setResolvedRefs(extractStoredResolvedRefs(stored));
                r.setErrorCode(DeviceErrorResponse.DeviceErrorCode.IDEMPOTENCY_CONFLICT.name());
                r.setErrorMessage("Conflito de idempotência: mesmo clientRequestId com payload diferente.");
                r.setConflictCode(DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name());
                return r;
            }
            JsonNode stored = safeReadTree(raced.getResultJson());
            DeviceOfflineCommandResultResponse r = toResult(raced, DeviceOfflineCommandStatus.DUPLICATE, extractStoredResult(stored));
            r.setResolvedRefs(extractStoredResolvedRefs(stored));
            return r;
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

        // resolve dependências (intra-batch ou histórico) antes de executar handler
        Map<String, Object> resolvedRefs = new HashMap<>();
        if (deps != null && !deps.isEmpty()) {
            for (String depId : deps) {
                ResolvedEntityRef dep = resolveDependency(device, depId, resolved);
                if (dep == null) {
                    cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
                    cmd.setFailedAt(Instant.now());
                    cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_NOT_FOUND.name());
                    cmd.setErrorMessage("Dependência localRef não encontrada: " + depId);
                    cmd.setConflictCode(DeviceOfflineConflictCode.LOCAL_REF_NOT_FOUND.name());
                    cmd.setDependencyStatus("MISSING");
                    repository.save(cmd);

                    operationalEventLogService.logPublicEvent(
                            tenant, inst, unidade, null, null,
                            OperationalEventType.DEVICE_OFFLINE_COMMAND_CONFLICT,
                            OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                            cmd.getId(),
                            OperationalOrigem.DEVICE_POS,
                            "Offline localRef dependency missing",
                            Map.of("tenantId", device.tenantId(), "deviceId", device.dispositivoId(), "commandId", cmd.getId(), "clientRequestId", cmd.getClientRequestId(), "missingDependsOn", depId),
                            ip, userAgent
                    );

                    DeviceOfflineCommandResultResponse r = toResult(cmd, DeviceOfflineCommandStatus.CONFLICT, null);
                    r.setLocalRef(localRef);
                    r.setDependsOn(deps);
                    r.setDependencyStatus("MISSING");
                    r.setResolvedRefs(resolvedRefs);
                    r.setErrorCode(cmd.getErrorCode());
                    r.setErrorMessage(cmd.getErrorMessage());
                    r.setConflictCode(cmd.getConflictCode());
                    outcomeByClientRequestId.put(clientRequestId, DeviceOfflineCommandStatus.CONFLICT);
                    return r;
                }
                DeviceOfflineCommandStatus depStatus = outcomeByClientRequestId.get(dep.sourceClientRequestId());
                if (depStatus != null && (depStatus == DeviceOfflineCommandStatus.CONFLICT || depStatus == DeviceOfflineCommandStatus.REJECTED || depStatus == DeviceOfflineCommandStatus.FAILED)) {
                    cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
                    cmd.setFailedAt(Instant.now());
                    cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_NOT_APPLIED.name());
                    cmd.setErrorMessage("Dependência falhou anteriormente: " + depId);
                    cmd.setConflictCode(DeviceOfflineConflictCode.LOCAL_REF_FAILED_DEPENDENCY.name());
                    cmd.setDependencyStatus("FAILED_DEPENDENCY");
                    repository.save(cmd);

                    DeviceOfflineCommandResultResponse r = toResult(cmd, DeviceOfflineCommandStatus.CONFLICT, null);
                    r.setLocalRef(localRef);
                    r.setDependsOn(deps);
                    r.setDependencyStatus("FAILED_DEPENDENCY");
                    r.setResolvedRefs(resolvedRefs);
                    r.setErrorCode(cmd.getErrorCode());
                    r.setErrorMessage(cmd.getErrorMessage());
                    r.setConflictCode(cmd.getConflictCode());
                    outcomeByClientRequestId.put(clientRequestId, DeviceOfflineCommandStatus.CONFLICT);
                    return r;
                }
                resolvedRefs.put(depId, Map.of("clientRequestId", dep.sourceClientRequestId(), "entityType", dep.entityType(), "entityId", dep.entityId()));
            }
            cmd.setDependencyStatus("RESOLVED");
        }

        JsonNode effectivePayload = applyResolvedRefsToPayload(type, req.getPayload(), resolvedRefs);

        String derivedIdempotencyKey = "offline:" + device.dispositivoId() + ":" + clientRequestId;
        try {
            var processed = processor.process(device, type, clientRequestId, effectivePayload, derivedIdempotencyKey, ip, userAgent);

            cmd.setStatus(DeviceOfflineCommandStatus.APPLIED);
            cmd.setProcessedAt(Instant.now());
            cmd.setCreatedEntityType(processed.createdEntityType());
            cmd.setCreatedEntityId(processed.createdEntityId());
            cmd.setResultJson(writeCanonicalJson(Map.of(
                    "result", processed.resultJson(),
                    "resolvedRefs", resolvedRefs
            )));
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

            if (cmd.getCreatedEntityType() != null && cmd.getCreatedEntityId() != null) {
                ResolvedEntityRef ref = new ResolvedEntityRef(cmd.getCreatedEntityType(), cmd.getCreatedEntityId(), clientRequestId);
                resolved.put(localRef, ref);
                resolved.put(clientRequestId, ref);
            }
            outcomeByClientRequestId.put(clientRequestId, DeviceOfflineCommandStatus.APPLIED);

            DeviceOfflineCommandResultResponse r = toResult(cmd, DeviceOfflineCommandStatus.APPLIED, processed.resultJson());
            r.setLocalRef(localRef);
            r.setDependsOn(deps);
            r.setDependencyStatus(cmd.getDependencyStatus());
            r.setResolvedRefs(resolvedRefs);
            return r;
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
            r.setLocalRef(localRef);
            r.setDependsOn(deps);
            r.setDependencyStatus(cmd.getDependencyStatus());
            r.setResolvedRefs(resolvedRefs);
            r.setErrorCode(ex.getCode().name());
            r.setErrorMessage(ex.getMessage());
            r.setConflictCode(cmd.getConflictCode());
            outcomeByClientRequestId.put(clientRequestId, st);
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
            r.setLocalRef(localRef);
            r.setDependsOn(deps);
            r.setDependencyStatus(cmd.getDependencyStatus());
            r.setResolvedRefs(resolvedRefs);
            r.setErrorCode(cmd.getErrorCode());
            r.setErrorMessage(cmd.getErrorMessage());
            outcomeByClientRequestId.put(clientRequestId, DeviceOfflineCommandStatus.FAILED);
            return r;
        }
    }

    private ResolvedEntityRef resolveDependency(DevicePrincipal device, String depId, Map<String, ResolvedEntityRef> resolved) {
        if (depId == null || depId.isBlank()) return null;
        String key = depId.trim();
        ResolvedEntityRef inBatch = resolved.get(key);
        if (inBatch != null) return inBatch;

        DeviceOfflineCommand existing = repository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(device.tenantId(), device.dispositivoId(), key).orElse(null);
        if (existing == null) return null;
        if (existing.getStatus() != DeviceOfflineCommandStatus.APPLIED && existing.getStatus() != DeviceOfflineCommandStatus.DUPLICATE) return null;
        if (existing.getCreatedEntityType() == null || existing.getCreatedEntityId() == null) return null;
        return new ResolvedEntityRef(existing.getCreatedEntityType(), existing.getCreatedEntityId(), existing.getClientRequestId());
    }

    private JsonNode applyResolvedRefsToPayload(DeviceOfflineCommandType type, JsonNode payload, Map<String, Object> resolvedRefs) {
        if (payload == null || resolvedRefs == null || resolvedRefs.isEmpty() || type == null) return payload;
        if (!(payload instanceof ObjectNode obj)) return payload;

        if (type == DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL && obj.hasNonNull("pedidoClientRequestId")) {
            String depId = obj.get("pedidoClientRequestId").asText();
            Object v = resolvedRefs.get(depId);
            if (v instanceof Map<?, ?> m) {
                Object entityType = m.get("entityType");
                Object entityId = m.get("entityId");
                if (!"PEDIDO".equals(String.valueOf(entityType))) {
                    throw new DeviceApiException(HttpStatus.CONFLICT,
                            DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_TYPE_MISMATCH,
                            "localRef não aponta para PEDIDO.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            Map.of("expected", "PEDIDO", "actual", String.valueOf(entityType)));
                }
                obj.put("pedidoId", Long.parseLong(String.valueOf(entityId)));
            }
        }

        if (type == DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT && obj.hasNonNull("ordemPagamentoClientRequestId")) {
            String depId = obj.get("ordemPagamentoClientRequestId").asText();
            Object v = resolvedRefs.get(depId);
            if (v instanceof Map<?, ?> m) {
                Object entityType = m.get("entityType");
                Object entityId = m.get("entityId");
                if (!"ORDEM_PAGAMENTO".equals(String.valueOf(entityType))) {
                    throw new DeviceApiException(HttpStatus.CONFLICT,
                            DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_TYPE_MISMATCH,
                            "localRef não aponta para ORDEM_PAGAMENTO.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            Map.of("expected", "ORDEM_PAGAMENTO", "actual", String.valueOf(entityType)));
                }
                obj.put("ordemPagamentoId", Long.parseLong(String.valueOf(entityId)));
            }
        }

        return obj;
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
            case OFFLINE_LOCALREF_NOT_FOUND -> DeviceOfflineConflictCode.LOCAL_REF_NOT_FOUND.name();
            case OFFLINE_LOCALREF_NOT_APPLIED -> DeviceOfflineConflictCode.LOCAL_REF_NOT_APPLIED.name();
            case OFFLINE_LOCALREF_TYPE_MISMATCH -> DeviceOfflineConflictCode.LOCAL_REF_ENTITY_TYPE_MISMATCH.name();
            case OFFLINE_LOCALREF_CIRCULAR_DEPENDENCY -> DeviceOfflineConflictCode.LOCAL_REF_CIRCULAR_DEPENDENCY.name();
            case OFFLINE_LOCALREF_FORWARD_REFERENCE_NOT_ALLOWED -> DeviceOfflineConflictCode.LOCAL_REF_FORWARD_REFERENCE_NOT_ALLOWED.name();
            case OFFLINE_DEPENDENCY_DEPTH_EXCEEDED -> DeviceOfflineConflictCode.DEPENDENCY_DEPTH_EXCEEDED.name();
            case OFFLINE_BATCH_PAYLOAD_TOO_LARGE -> DeviceOfflineConflictCode.BATCH_PAYLOAD_TOO_LARGE.name();
            case OFFLINE_COMMAND_PAYLOAD_TOO_LARGE -> DeviceOfflineConflictCode.PAYLOAD_TOO_LARGE.name();
            case OFFLINE_TOO_MANY_ITEMS -> DeviceOfflineConflictCode.TOO_MANY_ITEMS.name();
            default -> DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name();
        };
    }

    private DeviceOfflineCommandResultResponse toResult(DeviceOfflineCommand cmd, DeviceOfflineCommandStatus status, JsonNode result) {
        DeviceOfflineCommandResultResponse r = new DeviceOfflineCommandResultResponse();
        r.setClientRequestId(cmd.getClientRequestId());
        r.setLocalRef(cmd.getClientRequestId());
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

    private JsonNode extractStoredResult(JsonNode stored) {
        if (stored == null) return null;
        if (stored.has("result")) return stored.get("result");
        return stored;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStoredResolvedRefs(JsonNode stored) {
        if (stored == null || !stored.has("resolvedRefs")) return null;
        try {
            return objectMapper.convertValue(stored.get("resolvedRefs"), Map.class);
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
