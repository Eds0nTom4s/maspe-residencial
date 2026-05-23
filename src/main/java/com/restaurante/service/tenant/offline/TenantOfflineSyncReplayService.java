package com.restaurante.service.tenant.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.device.capability.service.DeviceCapabilityBootstrapService;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.entity.DeviceOfflineCommandReplayAttempt;
import com.restaurante.device.offline.entity.DeviceOfflineSyncSession;
import com.restaurante.device.offline.repository.DeviceOfflineCommandReplayAttemptRepository;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.device.offline.repository.DeviceOfflineSyncSessionRepository;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.OfflineCommandReplayBatchResponse;
import com.restaurante.dto.response.OfflineCommandReplayEligibilityResponse;
import com.restaurante.dto.response.OfflineCommandReplayPreviewResponse;
import com.restaurante.dto.response.OfflineCommandReplayResultResponse;
import com.restaurante.dto.response.OfflineSyncDiagnosticExportResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineConflictCode;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityReason;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.DeviceOfflineReplayStatus;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantOfflineSyncReplayService {

    private final TenantGuard tenantGuard;
    private final ObjectMapper objectMapper;
    private final DeviceOfflineSyncSessionRepository sessionRepository;
    private final DeviceOfflineCommandRepository commandRepository;
    private final DeviceOfflineCommandReplayAttemptRepository attemptRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final DeviceOperationalCapabilityRepository deviceOperationalCapabilityRepository;
    private final DeviceCapabilityBootstrapService capabilityBootstrapService;
    private final DeviceOfflineReplayEligibilityService eligibilityService;
    private final DeviceOfflineCommandProcessor processor;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public OfflineCommandReplayPreviewResponse preview(String serverSyncId,
                                                      List<DeviceOfflineCommandStatus> statuses,
                                                      List<DeviceOfflineCommandType> commandTypes,
                                                      boolean onlyEligible,
                                                      boolean includeWarnings,
                                                      String ip,
                                                      String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        DeviceOfflineSyncSession session = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), serverSyncId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", serverSyncId));

        List<DeviceOfflineCommand> candidates = commandRepository.listForReplay(
                ctx.tenantId(),
                serverSyncId,
                statuses != null ? statuses : List.of(),
                statuses == null || statuses.isEmpty(),
                commandTypes != null ? commandTypes : List.of(),
                commandTypes == null || commandTypes.isEmpty()
        );

        List<OfflineCommandReplayEligibilityResponse> items = new ArrayList<>(candidates.size());
        int eligible = 0, notEligibleCount = 0, review = 0;
        for (DeviceOfflineCommand cmd : candidates) {
            OfflineCommandReplayEligibilityResponse e = eligibilityService.evaluate(cmd, includeWarnings);
            if (onlyEligible && !e.isEligible()) continue;
            items.add(e);
            if (e.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.ELIGIBLE) eligible++;
            else if (e.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.REQUIRES_MANUAL_REVIEW) review++;
            else notEligibleCount++;
        }

        operationalEventLogService.logPublicEvent(
                session.getTenant(), null, session.getUnidadeAtendimento(), null, null,
                OperationalEventType.DEVICE_OFFLINE_REPLAY_PREVIEWED,
                OperationalEntityType.DEVICE_OFFLINE_SYNC,
                session.getId(),
                OperationalOrigem.SYSTEM,
                "Offline replay previewed",
                Map.of(
                        "tenantId", ctx.tenantId(),
                        "serverSyncId", serverSyncId,
                        "totalCommandsAnalyzed", candidates.size(),
                        "eligibleCount", eligible,
                        "notEligibleCount", notEligibleCount,
                        "requiresReviewCount", review,
                        "actorUserId", ctx.userId()
                ),
                ip, userAgent
        );

        OfflineCommandReplayPreviewResponse out = new OfflineCommandReplayPreviewResponse();
        out.setServerSyncId(serverSyncId);
        out.setTotalCommandsAnalyzed(candidates.size());
        out.setEligibleCount(eligible);
        out.setNotEligibleCount(notEligibleCount);
        out.setRequiresReviewCount(review);
        out.setItems(items);
        return out;
    }

    @Transactional
    public OfflineCommandReplayBatchResponse replaySession(String serverSyncId,
                                                          List<Long> commandIds,
                                                          List<DeviceOfflineCommandStatus> statuses,
                                                          List<DeviceOfflineCommandType> commandTypes,
                                                          String reason,
                                                          boolean dryRun,
                                                          boolean force,
                                                          String ip,
                                                          String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        if (reason == null || reason.isBlank()) throw new BusinessException("REPLAY_REASON_REQUIRED");

        DeviceOfflineSyncSession session = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), serverSyncId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", serverSyncId));

        List<DeviceOfflineCommand> candidates;
        if (commandIds != null && !commandIds.isEmpty()) {
            candidates = commandIds.stream()
                    .map(id -> commandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineCommand", "id", id)))
                    .filter(c -> c.getTenant().getId().equals(ctx.tenantId()) && serverSyncId.equals(c.getServerSyncId()))
                    .sorted(Comparator.comparing(DeviceOfflineCommand::getCommandIndex, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        } else {
            candidates = commandRepository.listForReplay(
                    ctx.tenantId(),
                    serverSyncId,
                    statuses != null ? statuses : List.of(),
                    statuses == null || statuses.isEmpty(),
                    commandTypes != null ? commandTypes : List.of(),
                    commandTypes == null || commandTypes.isEmpty()
            );
        }

        String operationId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        operationalEventLogService.logPublicEvent(
                session.getTenant(), null, session.getUnidadeAtendimento(), null, null,
                OperationalEventType.DEVICE_OFFLINE_REPLAY_REQUESTED,
                OperationalEntityType.DEVICE_OFFLINE_SYNC,
                session.getId(),
                OperationalOrigem.SYSTEM,
                "Offline replay requested",
                Map.of(
                        "tenantId", ctx.tenantId(),
                        "serverSyncId", serverSyncId,
                        "operationId", operationId,
                        "requested", candidates.size(),
                        "force", force,
                        "dryRun", dryRun,
                        "actorUserId", ctx.userId()
                ),
                ip, userAgent
        );

        List<OfflineCommandReplayResultResponse> results = new ArrayList<>(candidates.size());
        int succeeded = 0, noop = 0, blocked = 0, failed = 0;

        // constrói DevicePrincipal do device alvo (replay sempre valida permissões do device atual)
        DevicePrincipal replayDevice = buildDevicePrincipal(ctx.tenantId(), session.getDispositivoOperacional().getId(), ip, userAgent);

        // ordena para reduzir chance de replay de dependente antes da dependência (commandIndex asc)
        List<DeviceOfflineCommand> ordered = candidates.stream()
                .sorted(Comparator.comparing(DeviceOfflineCommand::getCommandIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        // mapa de resoluções intra-operação (reaproveita para dependsOn durante o replay)
        Map<String, ResolvedEntityRef> resolved = new HashMap<>();
        Map<String, DeviceOfflineCommandStatus> outcomeByClientRequestId = new HashMap<>();
        for (DeviceOfflineCommand cmd : ordered) {
            OfflineCommandReplayEligibilityResponse eligibility = eligibilityService.evaluate(cmd, true);

            if (dryRun) {
                OfflineCommandReplayResultResponse r = new OfflineCommandReplayResultResponse();
                r.setCommandId(cmd.getId());
                r.setClientRequestId(cmd.getClientRequestId());
                r.setCommandType(cmd.getCommandType());
                r.setPreviousStatus(cmd.getStatus());
                r.setReplayStatus(eligibility.isEligible() ? DeviceOfflineReplayStatus.REQUESTED : DeviceOfflineReplayStatus.BLOCKED);
                r.setEligibilityStatus(eligibility.getEligibilityStatus().name());
                r.setEligibilityReason(eligibility.getReason() != null ? eligibility.getReason().name() : null);
                results.add(r);
                if (eligibility.isEligible()) noop++; else blocked++;
                continue;
            }

            OfflineCommandReplayResultResponse r = replayOne(ctx, session, replayDevice, cmd.getId(), eligibility, reason, force, resolved, outcomeByClientRequestId, ip, userAgent);
            results.add(r);
            if (r.getReplayStatus() == DeviceOfflineReplayStatus.SUCCEEDED) succeeded++;
            else if (r.getReplayStatus() == DeviceOfflineReplayStatus.NOOP) noop++;
            else if (r.getReplayStatus() == DeviceOfflineReplayStatus.BLOCKED) blocked++;
            else if (r.getReplayStatus() == DeviceOfflineReplayStatus.FAILED) failed++;
        }

        OfflineCommandReplayBatchResponse out = new OfflineCommandReplayBatchResponse();
        out.setOperationId(operationId);
        out.setServerSyncId(serverSyncId);
        out.setRequestedAt(requestedAt);
        out.setRequested(candidates.size());
        out.setSucceeded(succeeded);
        out.setNoop(noop);
        out.setBlocked(blocked);
        out.setFailed(failed);
        out.setResults(results);
        return out;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected OfflineCommandReplayResultResponse replayOne(TenantContext ctx,
                                                          DeviceOfflineSyncSession session,
                                                          DevicePrincipal replayDevice,
                                                          Long commandId,
                                                          OfflineCommandReplayEligibilityResponse eligibility,
                                                          String reason,
                                                          boolean force,
                                                          Map<String, ResolvedEntityRef> resolved,
                                                          Map<String, DeviceOfflineCommandStatus> outcomeByClientRequestId,
                                                          String ip,
                                                          String userAgent) {
        DeviceOfflineCommand cmd = commandRepository.findForUpdateById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineCommand", "id", commandId));

        if (!cmd.getTenant().getId().equals(ctx.tenantId()) || !session.getServerSyncId().equals(cmd.getServerSyncId())) {
            throw new ResourceNotFoundException("DeviceOfflineCommand", "id", commandId);
        }

        DeviceOfflineCommandReplayAttempt attempt = new DeviceOfflineCommandReplayAttempt();
        attempt.setTenant(cmd.getTenant());
        attempt.setSyncSession(session);
        attempt.setServerSyncId(session.getServerSyncId());
        attempt.setCommand(cmd);
        attempt.setDispositivoOperacional(cmd.getDispositivoOperacional());
        attempt.setClientRequestId(cmd.getClientRequestId());
        attempt.setCommandType(cmd.getCommandType() != null ? cmd.getCommandType().name() : null);
        attempt.setPreviousStatus(cmd.getStatus() != null ? cmd.getStatus().name() : null);
        attempt.setEligibilityStatus(eligibility.getEligibilityStatus() != null ? eligibility.getEligibilityStatus().name() : DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE.name());
        attempt.setEligibilityReason(eligibility.getReason() != null ? eligibility.getReason().name() : null);
        attempt.setRequestedBy(ctx.userId());
        attempt.setAuditReason(reason);
        attempt.setAttemptNumber(attemptRepository.maxAttemptNumber(ctx.tenantId(), cmd.getId()) + 1);
        attempt.setReplayStatus(DeviceOfflineReplayStatus.REQUESTED.name());
        attemptRepository.save(attempt);

        OfflineCommandReplayResultResponse r = new OfflineCommandReplayResultResponse();
        r.setCommandId(cmd.getId());
        r.setClientRequestId(cmd.getClientRequestId());
        r.setCommandType(cmd.getCommandType());
        r.setPreviousStatus(cmd.getStatus());
        r.setEligibilityStatus(attempt.getEligibilityStatus());
        r.setEligibilityReason(attempt.getEligibilityReason());

        // hard rules: nunca
        if (cmd.getStatus() == DeviceOfflineCommandStatus.APPLIED) return blocked(attempt, r, DeviceOfflineReplayEligibilityReason.COMMAND_ALREADY_APPLIED, ip, userAgent);
        if (cmd.getStatus() == DeviceOfflineCommandStatus.DUPLICATE) return blocked(attempt, r, DeviceOfflineReplayEligibilityReason.COMMAND_DUPLICATE, ip, userAgent);
        if (DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name().equals(cmd.getConflictCode())) return blocked(attempt, r, DeviceOfflineReplayEligibilityReason.IDEMPOTENCY_CONFLICT_NOT_REPLAYABLE, ip, userAgent);

        if (!eligibility.isEligible()) {
            if (!force) return blocked(attempt, r, eligibility.getReason() != null ? eligibility.getReason() : DeviceOfflineReplayEligibilityReason.STATUS_NOT_REPLAYABLE, ip, userAgent);
            // com force, ainda assim não reprocessa REQUIRES_MANUAL_REVIEW / NOT_ELIGIBLE automaticamente
            return blocked(attempt, r, eligibility.getReason() != null ? eligibility.getReason() : DeviceOfflineReplayEligibilityReason.STATUS_NOT_REPLAYABLE, ip, userAgent);
        }

        attempt.setReplayStatus(DeviceOfflineReplayStatus.RUNNING.name());
        attempt.setStartedAt(Instant.now());
        attemptRepository.save(attempt);

        try {
            // resolve dependências e reescreve payload, reaproveitando regras do offline sync normal
            Map<String, Object> resolvedRefs = new HashMap<>();
            List<String> deps = inferDependsOnFromStored(cmd);
            if (deps != null && !deps.isEmpty()) {
                for (String depId : deps) {
                    ResolvedEntityRef dep = resolveDependency(cmd, depId, resolved);
                    if (dep == null) {
                        cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
                        cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_NOT_FOUND.name());
                        cmd.setErrorMessage("Dependência não encontrada: " + depId);
                        cmd.setConflictCode(DeviceOfflineConflictCode.LOCAL_REF_NOT_FOUND.name());
                        cmd.setDependencyStatus("MISSING");
                        commandRepository.save(cmd);

                        attempt.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED.name());
                        attempt.setFinishedAt(Instant.now());
                        attempt.setResultStatus("CONFLICT");
                        attempt.setErrorCode(cmd.getErrorCode());
                        attempt.setErrorMessage(cmd.getErrorMessage());
                        attemptRepository.save(attempt);

                        outcomeByClientRequestId.put(cmd.getClientRequestId(), DeviceOfflineCommandStatus.CONFLICT);
                        r.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED);
                        r.setResultStatus("CONFLICT");
                        r.setErrorCode(cmd.getErrorCode());
                        r.setErrorMessage(cmd.getErrorMessage());
                        return r;
                    }
                    DeviceOfflineCommandStatus depStatus = outcomeByClientRequestId.get(dep.sourceClientRequestId());
                    if (depStatus != null && (depStatus == DeviceOfflineCommandStatus.CONFLICT || depStatus == DeviceOfflineCommandStatus.REJECTED || depStatus == DeviceOfflineCommandStatus.FAILED)) {
                        cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
                        cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.OFFLINE_LOCALREF_NOT_APPLIED.name());
                        cmd.setErrorMessage("Dependência falhou anteriormente: " + depId);
                        cmd.setConflictCode(DeviceOfflineConflictCode.LOCAL_REF_FAILED_DEPENDENCY.name());
                        cmd.setDependencyStatus("FAILED_DEPENDENCY");
                        commandRepository.save(cmd);

                        attempt.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED.name());
                        attempt.setFinishedAt(Instant.now());
                        attempt.setResultStatus("CONFLICT");
                        attempt.setErrorCode(cmd.getErrorCode());
                        attempt.setErrorMessage(cmd.getErrorMessage());
                        attemptRepository.save(attempt);

                        outcomeByClientRequestId.put(cmd.getClientRequestId(), DeviceOfflineCommandStatus.CONFLICT);
                        r.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED);
                        r.setResultStatus("CONFLICT");
                        r.setErrorCode(cmd.getErrorCode());
                        r.setErrorMessage(cmd.getErrorMessage());
                        return r;
                    }
                    resolvedRefs.put(depId, Map.of("clientRequestId", dep.sourceClientRequestId(), "entityType", dep.entityType(), "entityId", dep.entityId()));
                }
                cmd.setDependencyStatus("RESOLVED");
            }

            JsonNode payload = safeReadTree(cmd.getPayloadJson());
            JsonNode effectivePayload = applyResolvedRefsToPayload(cmd.getCommandType(), payload, resolvedRefs);

            // valida capability por tipo do comando (replay mantém as mesmas regras do device offline)
            requireOfflineCapabilities(replayDevice, cmd.getCommandType(), ip, userAgent);

            String derivedIdempotencyKey = "offline:" + replayDevice.dispositivoId() + ":" + cmd.getClientRequestId();
            var processed = processor.process(replayDevice, cmd.getCommandType(), cmd.getClientRequestId(), effectivePayload, derivedIdempotencyKey, ip, userAgent);

            cmd.setStatus(DeviceOfflineCommandStatus.APPLIED);
            cmd.setProcessedAt(Instant.now());
            cmd.setCreatedEntityType(processed.createdEntityType());
            cmd.setCreatedEntityId(processed.createdEntityId());
            cmd.setResultJson(writeCanonicalJson(Map.of("result", processed.resultJson(), "resolvedRefs", resolvedRefs)));
            cmd.setErrorCode(null);
            cmd.setErrorMessage(null);
            cmd.setConflictCode(null);
            cmd.setReplayCount(cmd.getReplayCount() + 1);
            cmd.setLastReplayAttemptAt(Instant.now());
            commandRepository.save(cmd);

            attempt.setReplayStatus(DeviceOfflineReplayStatus.SUCCEEDED.name());
            attempt.setFinishedAt(Instant.now());
            attempt.setResultStatus("APPLIED");
            attempt.setCreatedEntityType(cmd.getCreatedEntityType());
            attempt.setCreatedEntityId(cmd.getCreatedEntityId());
            attempt.setResultJson(writeCanonicalJson(Map.of("result", processed.resultJson())));
            attemptRepository.save(attempt);

            // vincula attempt ao comando
            cmd.setLastReplayAttempt(attempt);
            commandRepository.save(cmd);

            operationalEventLogService.logPublicEvent(
                    cmd.getTenant(), null, cmd.getUnidadeAtendimento(), null, null,
                    OperationalEventType.DEVICE_OFFLINE_REPLAY_COMMAND_SUCCEEDED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    cmd.getId(),
                    OperationalOrigem.SYSTEM,
                    "Offline replay command succeeded",
                    Map.of(
                            "tenantId", ctx.tenantId(),
                            "serverSyncId", session.getServerSyncId(),
                            "commandId", cmd.getId(),
                            "clientRequestId", cmd.getClientRequestId(),
                            "commandType", cmd.getCommandType().name(),
                            "previousStatus", attempt.getPreviousStatus(),
                            "actorUserId", ctx.userId()
                    ),
                    ip, userAgent
            );

            if (cmd.getCreatedEntityType() != null && cmd.getCreatedEntityId() != null) {
                ResolvedEntityRef ref = new ResolvedEntityRef(cmd.getCreatedEntityType(), cmd.getCreatedEntityId(), cmd.getClientRequestId());
                resolved.put(cmd.getClientRequestId(), ref);
            }
            outcomeByClientRequestId.put(cmd.getClientRequestId(), DeviceOfflineCommandStatus.APPLIED);

            r.setReplayStatus(DeviceOfflineReplayStatus.SUCCEEDED);
            r.setResultStatus("APPLIED");
            r.setCreatedEntityType(cmd.getCreatedEntityType());
            r.setCreatedEntityId(cmd.getCreatedEntityId());
            return r;
        } catch (Exception ex) {
            cmd.setStatus(DeviceOfflineCommandStatus.FAILED);
            cmd.setFailedAt(Instant.now());
            cmd.setErrorCode(DeviceErrorResponse.DeviceErrorCode.DEVICE_INTERNAL_ERROR.name());
            cmd.setErrorMessage("Falha no replay do comando offline.");
            commandRepository.save(cmd);

            attempt.setReplayStatus(DeviceOfflineReplayStatus.FAILED.name());
            attempt.setFinishedAt(Instant.now());
            attempt.setResultStatus("FAILED");
            attempt.setErrorCode(DeviceErrorResponse.DeviceErrorCode.DEVICE_INTERNAL_ERROR.name());
            attempt.setErrorMessage(ex.getMessage());
            attemptRepository.save(attempt);

            cmd.setLastReplayAttempt(attempt);
            cmd.setReplayCount(cmd.getReplayCount() + 1);
            cmd.setLastReplayAttemptAt(Instant.now());
            commandRepository.save(cmd);

            operationalEventLogService.logPublicEvent(
                    cmd.getTenant(), null, cmd.getUnidadeAtendimento(), null, null,
                    OperationalEventType.DEVICE_OFFLINE_REPLAY_COMMAND_FAILED,
                    OperationalEntityType.DEVICE_OFFLINE_COMMAND,
                    cmd.getId(),
                    OperationalOrigem.SYSTEM,
                    "Offline replay command failed",
                    Map.of(
                            "tenantId", ctx.tenantId(),
                            "serverSyncId", session.getServerSyncId(),
                            "commandId", cmd.getId(),
                            "clientRequestId", cmd.getClientRequestId(),
                            "commandType", cmd.getCommandType().name(),
                            "previousStatus", attempt.getPreviousStatus(),
                            "actorUserId", ctx.userId(),
                            "errorCode", attempt.getErrorCode()
                    ),
                    ip, userAgent
            );

            outcomeByClientRequestId.put(cmd.getClientRequestId(), DeviceOfflineCommandStatus.FAILED);
            r.setReplayStatus(DeviceOfflineReplayStatus.FAILED);
            r.setResultStatus("FAILED");
            r.setErrorCode(attempt.getErrorCode());
            r.setErrorMessage(attempt.getErrorMessage());
            return r;
        }
    }

    @Transactional(readOnly = true)
    public Page<DeviceOfflineCommandReplayAttempt> listAttemptsForSession(String serverSyncId, Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        Long tenantId = TenantContextHolder.get().orElseThrow().tenantId();
        return attemptRepository.findByTenantAndServerSyncId(tenantId, serverSyncId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DeviceOfflineCommandReplayAttempt> listAttemptsForCommand(Long commandId, Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        Long tenantId = TenantContextHolder.get().orElseThrow().tenantId();
        return attemptRepository.findByTenantAndCommand(tenantId, commandId, pageable);
    }

    @Transactional(readOnly = true)
    public OfflineSyncDiagnosticExportResponse exportDiagnostic(String serverSyncId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        DeviceOfflineSyncSession session = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), serverSyncId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", serverSyncId));

        List<DeviceOfflineCommand> cmds = commandRepository.listForReplay(ctx.tenantId(), serverSyncId, List.of(), true, List.of(), true);

        Page<DeviceOfflineCommandReplayAttempt> attempts = attemptRepository.findByTenantAndServerSyncId(ctx.tenantId(), serverSyncId, PageRequest.of(0, 500));

        operationalEventLogService.logPublicEvent(
                session.getTenant(), null, session.getUnidadeAtendimento(), null, null,
                OperationalEventType.DEVICE_OFFLINE_DIAGNOSTIC_EXPORTED,
                OperationalEntityType.DEVICE_OFFLINE_SYNC,
                session.getId(),
                OperationalOrigem.SYSTEM,
                "Offline diagnostic exported",
                Map.of("tenantId", ctx.tenantId(), "serverSyncId", serverSyncId, "actorUserId", ctx.userId()),
                ip, userAgent
        );

        OfflineSyncDiagnosticExportResponse out = new OfflineSyncDiagnosticExportResponse();

        OfflineSyncDiagnosticExportResponse.SessionInfo si = new OfflineSyncDiagnosticExportResponse.SessionInfo();
        si.setServerSyncId(session.getServerSyncId());
        si.setSyncSessionId(session.getSyncSessionId());
        si.setStatus(session.getStatus() != null ? session.getStatus().name() : null);
        si.setUnidadeId(session.getUnidadeAtendimento() != null ? session.getUnidadeAtendimento().getId() : null);
        si.setDeviceId(session.getDispositivoOperacional() != null ? session.getDispositivoOperacional().getId() : null);
        si.setAppVersion(session.getAppVersion());
        si.setReceivedAt(session.getReceivedAt());
        si.setFinishedAt(session.getFinishedProcessingAt());
        si.setDurationMs(session.getDurationMs());
        si.setTotalCommands(session.getTotalCommands());
        si.setApplied(session.getAppliedCount());
        si.setDuplicates(session.getDuplicateCount());
        si.setRejected(session.getRejectedCount());
        si.setConflicts(session.getConflictCount());
        si.setFailed(session.getFailedCount());
        out.setSession(si);

        List<OfflineSyncDiagnosticExportResponse.CommandInfo> commandInfos = cmds.stream().map(c -> {
            OfflineSyncDiagnosticExportResponse.CommandInfo ci = new OfflineSyncDiagnosticExportResponse.CommandInfo();
            ci.setCommandId(c.getId());
            ci.setClientRequestId(c.getClientRequestId());
            ci.setCommandType(c.getCommandType() != null ? c.getCommandType().name() : null);
            ci.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
            ci.setConflictCode(c.getConflictCode());
            ci.setErrorCode(c.getErrorCode());
            ci.setPayloadHash(c.getPayloadHash());
            ci.setPayloadSizeBytes(c.getPayloadSizeBytes());
            ci.setCommandIndex(c.getCommandIndex());
            ci.setDependsOnClientRequestId(c.getDependsOnClientRequestId());
            ci.setDependencyStatus(c.getDependencyStatus());
            ci.setCreatedEntityType(c.getCreatedEntityType());
            ci.setCreatedEntityId(c.getCreatedEntityId());
            ci.setReplayCount(c.getReplayCount());
            ci.setLastReplayAttemptAt(c.getLastReplayAttemptAt());
            return ci;
        }).toList();
        out.setCommands(commandInfos);

        List<OfflineSyncDiagnosticExportResponse.ReplayAttemptInfo> attemptInfos = attempts.getContent().stream().map(a -> {
            OfflineSyncDiagnosticExportResponse.ReplayAttemptInfo ai = new OfflineSyncDiagnosticExportResponse.ReplayAttemptInfo();
            ai.setAttemptId(a.getId());
            ai.setCommandId(a.getCommand() != null ? a.getCommand().getId() : null);
            ai.setPreviousStatus(a.getPreviousStatus());
            ai.setReplayStatus(a.getReplayStatus());
            ai.setEligibilityStatus(a.getEligibilityStatus());
            ai.setEligibilityReason(a.getEligibilityReason());
            ai.setRequestedAt(a.getRequestedAt());
            ai.setRequestedBy(a.getRequestedBy());
            ai.setResultStatus(a.getResultStatus());
            ai.setErrorCode(a.getErrorCode());
            return ai;
        }).toList();
        out.setReplayAttempts(attemptInfos);

        return out;
    }

    @Transactional
    public OfflineCommandReplayResultResponse replaySingleCommand(Long commandId, String reason, boolean force, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        if (reason == null || reason.isBlank()) throw new BusinessException("REPLAY_REASON_REQUIRED");

        DeviceOfflineCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineCommand", "id", commandId));
        if (!cmd.getTenant().getId().equals(ctx.tenantId())) throw new ResourceNotFoundException("DeviceOfflineCommand", "id", commandId);
        if (cmd.getServerSyncId() == null || cmd.getServerSyncId().isBlank()) throw new BusinessException("REPLAY_COMMAND_NOT_ASSOCIATED_TO_SESSION");

        DeviceOfflineSyncSession session = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), cmd.getServerSyncId())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", cmd.getServerSyncId()));

        DevicePrincipal replayDevice = buildDevicePrincipal(ctx.tenantId(), cmd.getDispositivoOperacional().getId(), ip, userAgent);
        OfflineCommandReplayEligibilityResponse eligibility = eligibilityService.evaluate(cmd, true);

        Map<String, ResolvedEntityRef> resolved = new HashMap<>();
        Map<String, DeviceOfflineCommandStatus> outcome = new HashMap<>();
        return replayOne(ctx, session, replayDevice, cmd.getId(), eligibility, reason, force, resolved, outcome, ip, userAgent);
    }

    private OfflineCommandReplayResultResponse blocked(DeviceOfflineCommandReplayAttempt attempt,
                                                       OfflineCommandReplayResultResponse r,
                                                       DeviceOfflineReplayEligibilityReason reason,
                                                       String ip,
                                                       String userAgent) {
        attempt.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED.name());
        attempt.setFinishedAt(Instant.now());
        attempt.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE.name());
        attempt.setEligibilityReason(reason != null ? reason.name() : null);
        attempt.setResultStatus("BLOCKED");
        attemptRepository.save(attempt);

        r.setReplayStatus(DeviceOfflineReplayStatus.BLOCKED);
        r.setResultStatus("BLOCKED");
        return r;
    }

    private record ResolvedEntityRef(String entityType, Long entityId, String sourceClientRequestId) {}

    private DevicePrincipal buildDevicePrincipal(Long tenantId, Long deviceId, String ip, String userAgent) {
        DispositivoOperacional device = dispositivoOperacionalRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DispositivoOperacional", "id", deviceId));

        // garante defaults no banco (capabilities)
        capabilityBootstrapService.ensureDefaults(device, ip, userAgent);
        List<DeviceOperationalCapabilityEntity> capEntities = deviceOperationalCapabilityRepository.findByTenantAndDevice(tenantId, deviceId);
        List<DeviceCapability> enabledCaps = capEntities.stream()
                .filter(DeviceOperationalCapabilityEntity::isEnabled)
                .map(DeviceOperationalCapabilityEntity::getCapability)
                .toList();

        return new DevicePrincipal(
                device.getId(),
                device.getCodigo(),
                tenantId,
                device.getTenant() != null ? device.getTenant().getTenantCode() : null,
                device.getInstituicao() != null ? device.getInstituicao().getId() : null,
                device.getUnidadeAtendimento() != null ? device.getUnidadeAtendimento().getId() : null,
                null,
                device.getTipo(),
                device.getStatus() != null ? device.getStatus() : DispositivoStatus.ATIVO,
                enabledCaps,
                device.getTokenVersion() != null ? device.getTokenVersion() : 1
        );
    }

    private void requireOfflineCapabilities(DevicePrincipal device, DeviceOfflineCommandType type, String ip, String userAgent) {
        if (type == null) return;
        Set<DeviceCapability> caps = device.capabilities() != null ? Set.copyOf(device.capabilities()) : Set.of();
        DeviceCapability required = switch (type) {
            case CREATE_PEDIDO_POS -> DeviceCapability.OFFLINE_CREATE_ORDER;
            case CREATE_ORDEM_PAGAMENTO_MANUAL -> DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER;
            case CONFIRM_MANUAL_PAYMENT -> DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT;
            case REGISTER_LOCAL_ACTIVITY -> null;
        };
        if (required == null) return;
        if (!caps.contains(required)) {
            throw new BusinessException("DEVICE_OFFLINE_CAPABILITY_REQUIRED");
        }
    }

    private List<String> inferDependsOnFromStored(DeviceOfflineCommand cmd) {
        if (cmd == null || cmd.getDependsOnClientRequestId() == null || cmd.getDependsOnClientRequestId().isBlank()) return List.of();
        return List.of(cmd.getDependsOnClientRequestId().trim());
    }

    private ResolvedEntityRef resolveDependency(DeviceOfflineCommand cmd, String depId, Map<String, ResolvedEntityRef> resolved) {
        if (depId == null || depId.isBlank()) return null;
        String key = depId.trim();
        ResolvedEntityRef inBatch = resolved.get(key);
        if (inBatch != null) return inBatch;

        DeviceOfflineCommand existing = commandRepository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(cmd.getTenant().getId(), cmd.getDispositivoOperacional().getId(), key).orElse(null);
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
                    throw new BusinessException("OFFLINE_LOCALREF_TYPE_MISMATCH");
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
                    throw new BusinessException("OFFLINE_LOCALREF_TYPE_MISMATCH");
                }
                obj.put("ordemPagamentoId", Long.parseLong(String.valueOf(entityId)));
            }
        }

        return obj;
    }

    private JsonNode safeReadTree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeCanonicalJson(Object value) {
        try {
            ObjectMapper mapper = objectMapper.copy();
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
