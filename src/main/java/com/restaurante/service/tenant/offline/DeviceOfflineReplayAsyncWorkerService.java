package com.restaurante.service.tenant.offline;

import com.restaurante.config.DeviceOfflineReplayAsyncProperties;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.entity.DeviceOfflineReplayOperation;
import com.restaurante.device.offline.entity.DeviceOfflineReplayOperationItem;
import com.restaurante.device.offline.entity.DeviceOfflineSyncSession;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.device.offline.repository.DeviceOfflineReplayOperationItemRepository;
import com.restaurante.device.offline.repository.DeviceOfflineReplayOperationRepository;
import com.restaurante.device.offline.repository.DeviceOfflineSyncSessionRepository;
import com.restaurante.dto.response.OfflineCommandReplayEligibilityResponse;
import com.restaurante.dto.response.OfflineCommandReplayResultResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;
import com.restaurante.model.enums.DeviceOfflineReplayStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceOfflineReplayAsyncWorkerService {

    private final DeviceOfflineReplayAsyncProperties props;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final DeviceOfflineSyncSessionRepository sessionRepository;
    private final DeviceOfflineReplayOperationRepository operationRepository;
    private final DeviceOfflineReplayOperationItemRepository itemRepository;
    private final DeviceOfflineCommandRepository commandRepository;
    private final DeviceOfflineReplayEligibilityService eligibilityService;
    private final TenantOfflineSyncReplayService replayService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public void processOneEligibleOperation() {
        if (!props.isEnabled() || !props.isWorkerEnabled()) return;

        Instant now = Instant.now();
        Optional<DeviceOfflineReplayOperation> opt = operationRepository.findNextEligible(
                List.of(DeviceOfflineReplayOperationStatus.PENDING, DeviceOfflineReplayOperationStatus.RUNNING),
                now
        );
        if (opt.isEmpty()) return;
        DeviceOfflineReplayOperation op = opt.get();
        Long tenantId = op.getTenant().getId();

        Instant lockExpiredAt = now.minus(props.getLockTimeoutSeconds(), ChronoUnit.SECONDS);
        String lockedBy = workerId();
        int locked = operationRepository.tryLock(tenantId, op.getId(), now, lockExpiredAt, lockedBy);
        if (locked != 1) return;

        op = operationRepository.findById(op.getId()).orElseThrow();
        try {
            if (op.getStatus() == DeviceOfflineReplayOperationStatus.PENDING) {
                op.setStatus(DeviceOfflineReplayOperationStatus.RUNNING);
                op.setStartedAt(now);
                op.setLastProgressAt(now);
                operationRepository.save(op);

                operationalEventLogService.logPublicEvent(
                        op.getTenant(), null, op.getSyncSession().getUnidadeAtendimento(), null, null,
                        OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_STARTED,
                        OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                        op.getId(),
                        OperationalOrigem.SYSTEM,
                        "Offline replay operation started",
                        Map.of(
                                "tenantId", tenantId,
                                "serverSyncId", op.getServerSyncId(),
                                "operationId", op.getOperationId(),
                                "totalItems", op.getTotalItems()
                        ),
                        null, null
                );
            }

            DeviceOfflineSyncSession session = sessionRepository.findById(op.getSyncSession().getId()).orElseThrow();
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            String tenantCode = tenant != null ? tenant.getTenantCode() : null;
            TenantContext actorCtx = new TenantContext(tenantId, tenantCode, op.getRequestedBy(), java.util.Set.of(), TenantResolutionSource.LEGACY_NONE, false, false);

            DevicePrincipal replayDevice = replayService.buildDevicePrincipal(tenantId, session.getDispositivoOperacional().getId(), null, null);

            int processedThisRun = 0;
            while (processedThisRun < props.getBatchSize()) {
                List<Long> claimed = claimNextItemIds(tenantId, op.getId(), Math.min(props.getBatchSize() - processedThisRun, 50), now, lockExpiredAt, lockedBy);
                if (claimed.isEmpty()) break;
                for (Long itemId : claimed) {
                    processedThisRun++;
                    processOneItem(actorCtx, op.getId(), op.getOperationId(), session, replayDevice, itemId);
                    refreshAndMaybeEmitProgress(op.getId(), tenantId);
                }
            }

            finalizeIfDone(op.getId(), tenantId);
        } finally {
            DeviceOfflineReplayOperation latest = operationRepository.findById(op.getId()).orElse(null);
            if (latest != null) {
                latest.clearLock();
                operationRepository.save(latest);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processOneItem(TenantContext actorCtx,
                                 Long operationDbId,
                                 String operationId,
                                 DeviceOfflineSyncSession session,
                                 DevicePrincipal replayDevice,
                                 Long itemId) {
        DeviceOfflineReplayOperationItem item = itemRepository.findById(itemId).orElseThrow();
        if (item.getItemStatus() != DeviceOfflineReplayOperationItemStatus.RUNNING) return;

        DeviceOfflineCommand cmd = commandRepository.findById(item.getCommand().getId()).orElseThrow();

        // reavaliar elegibilidade no momento do processamento
        OfflineCommandReplayEligibilityResponse eligibility = eligibilityService.evaluate(cmd, false);
        item.setEligibilityStatus(eligibility.getEligibilityStatus() != null ? eligibility.getEligibilityStatus().name() : null);
        item.setEligibilityReason(eligibility.getReason() != null ? eligibility.getReason().name() : null);
        itemRepository.save(item);

        boolean eligible = eligibility.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.ELIGIBLE;
        if (!eligible && item.getOperation().isForce() && eligibility.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.REQUIRES_MANUAL_REVIEW) {
            eligible = true;
        }
        if (!eligible) {
            item.setItemStatus(DeviceOfflineReplayOperationItemStatus.BLOCKED);
            item.setFinishedAt(Instant.now());
            item.setErrorCode("OFFLINE_REPLAY_NOT_ELIGIBLE");
            item.setErrorMessage("Comando não elegível para replay.");
            itemRepository.save(item);
            commandRepository.clearReplayInProgress(actorCtx.tenantId(), cmd.getId(), operationDbId);
            return;
        }

        item.setAttempts(item.getAttempts() + 1);
        itemRepository.save(item);

        try {
            OfflineCommandReplayResultResponse result = replayService.replayOne(
                    actorCtx,
                    session,
                    replayDevice,
                    cmd.getId(),
                    eligibility,
                    item.getOperation().getReason(),
                    item.getOperation().isForce(),
                    new HashMap<>(), // replayOne mantém mapa interno por clientRequestId (resolverá entre batches via repo)
                    new HashMap<>(),
                    null,
                    null
            );

            String status = result.getResultStatus();
            if ("APPLIED".equals(status)) {
                item.setItemStatus(DeviceOfflineReplayOperationItemStatus.SUCCEEDED);
                item.setResultStatus("APPLIED");
                item.setFinishedAt(Instant.now());
                DeviceOfflineCommand fresh = commandRepository.findById(cmd.getId()).orElse(null);
                if (fresh != null && fresh.getLastReplayAttempt() != null) item.setReplayAttempt(fresh.getLastReplayAttempt());
                itemRepository.save(item);
                commandRepository.clearReplayInProgress(actorCtx.tenantId(), cmd.getId(), operationDbId);
                return;
            }

            if (result.getReplayStatus() == DeviceOfflineReplayStatus.BLOCKED) {
                item.setItemStatus(DeviceOfflineReplayOperationItemStatus.BLOCKED);
                item.setResultStatus(status);
                item.setErrorCode(result.getErrorCode());
                item.setErrorMessage(result.getErrorMessage());
                item.setFinishedAt(Instant.now());
                DeviceOfflineCommand fresh = commandRepository.findById(cmd.getId()).orElse(null);
                if (fresh != null && fresh.getLastReplayAttempt() != null) item.setReplayAttempt(fresh.getLastReplayAttempt());
                itemRepository.save(item);
                commandRepository.clearReplayInProgress(actorCtx.tenantId(), cmd.getId(), operationDbId);
                return;
            }

            // fallback: trata como falha retryable
            scheduleRetryOrFail(actorCtx.tenantId(), operationDbId, cmd.getId(), item, "OFFLINE_REPLAY_FAILED", result.getErrorMessage());
        } catch (Exception ex) {
            scheduleRetryOrFail(actorCtx.tenantId(), operationDbId, cmd.getId(), item, "OFFLINE_REPLAY_FAILED", ex.getMessage());
        }
    }

    private void scheduleRetryOrFail(Long tenantId,
                                     Long operationDbId,
                                     Long commandId,
                                     DeviceOfflineReplayOperationItem item,
                                     String errorCode,
                                     String errorMessage) {
        int attempts = item.getAttempts();
        if (attempts < props.getMaxAttemptsPerItem()) {
            item.setItemStatus(DeviceOfflineReplayOperationItemStatus.PENDING);
            item.setErrorCode(errorCode);
            item.setErrorMessage(errorMessage);
            item.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds(attempts)));
            item.setLockedAt(null);
            item.setLockedBy(null);
            itemRepository.save(item);
            commandRepository.clearReplayInProgress(tenantId, commandId, operationDbId);
            return;
        }

        item.setItemStatus(DeviceOfflineReplayOperationItemStatus.FAILED);
        item.setErrorCode(errorCode);
        item.setErrorMessage(errorMessage);
        item.setFinishedAt(Instant.now());
        itemRepository.save(item);
        commandRepository.clearReplayInProgress(tenantId, commandId, operationDbId);
    }

    private long backoffSeconds(int attemptNumber) {
        long seconds = (long) props.getInitialBackoffSeconds() * (1L << Math.max(0, attemptNumber - 1));
        return Math.min(seconds, props.getMaxBackoffSeconds());
    }

    private void refreshAndMaybeEmitProgress(Long operationDbId, Long tenantId) {
        DeviceOfflineReplayOperation op = operationRepository.findById(operationDbId).orElse(null);
        if (op == null) return;

        recomputeCounters(op, tenantId);
        operationRepository.save(op);

        maybeEmitProgressEvent(op);
    }

    private void recomputeCounters(DeviceOfflineReplayOperation op, Long tenantId) {
        long pending = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.PENDING));
        long running = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.RUNNING));
        long succ = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.SUCCEEDED));
        long noop = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.NOOP));
        long blocked = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.BLOCKED));
        long failed = itemRepository.countByOperationAndStatuses(tenantId, op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.FAILED));

        op.setPendingItems((int) pending);
        op.setRunningItems((int) running);
        op.setSucceededItems((int) succ);
        op.setNoopItems((int) noop);
        op.setBlockedItems((int) blocked);
        op.setFailedItems((int) failed);

        int processed = (int) (succ + noop + blocked + failed);
        int percent = op.getTotalItems() <= 0 ? 100 : Math.min(100, (processed * 100) / op.getTotalItems());
        op.setProgressPercent(percent);
        op.setLastProgressAt(Instant.now());
    }

    private void maybeEmitProgressEvent(DeviceOfflineReplayOperation op) {
        Instant now = Instant.now();
        Integer lastPct = op.getLastProgressEventPercent();
        Instant lastAt = op.getLastProgressEventAt();
        boolean pctOk = lastPct == null || (op.getProgressPercent() - lastPct) >= props.getProgressEventMinPercentDelta();
        boolean timeOk = lastAt == null || lastAt.plusSeconds(props.getProgressEventMinIntervalSeconds()).isBefore(now);

        boolean isFinal = op.getProgressPercent() >= 100
                || op.getPendingItems() == 0 && op.getRunningItems() == 0;

        if ((pctOk && timeOk) || isFinal) {
            op.setLastProgressEventAt(now);
            op.setLastProgressEventPercent(op.getProgressPercent());
            operationRepository.save(op);

            operationalEventLogService.logPublicEvent(
                    op.getTenant(), null, op.getSyncSession().getUnidadeAtendimento(), null, null,
                    OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_PROGRESS,
                    OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                    op.getId(),
                    OperationalOrigem.SYSTEM,
                    "Offline replay operation progress",
                    Map.of(
                            "tenantId", op.getTenant().getId(),
                            "operationId", op.getOperationId(),
                            "serverSyncId", op.getServerSyncId(),
                            "progressPercent", op.getProgressPercent(),
                            "succeededItems", op.getSucceededItems(),
                            "blockedItems", op.getBlockedItems(),
                            "failedItems", op.getFailedItems()
                    ),
                    null, null
            );
        }
    }

    private void finalizeIfDone(Long operationDbId, Long tenantId) {
        DeviceOfflineReplayOperation op = operationRepository.findById(operationDbId).orElse(null);
        if (op == null) return;
        recomputeCounters(op, tenantId);

        if (op.getPendingItems() > 0 || op.getRunningItems() > 0) {
            operationRepository.save(op);
            return;
        }

        if (op.getFailedItems() > 0) {
            op.setStatus(DeviceOfflineReplayOperationStatus.PARTIAL_FAILED);
        } else if (op.getBlockedItems() > 0 || op.getNoopItems() > 0) {
            op.setStatus(DeviceOfflineReplayOperationStatus.COMPLETED_WITH_NOOPS);
        } else {
            op.setStatus(DeviceOfflineReplayOperationStatus.COMPLETED);
        }
        op.setFinishedAt(Instant.now());
        operationRepository.save(op);

        // libera qualquer comando ainda preso por esta operação (safety net)
        commandRepository.clearReplayInProgressForOperation(tenantId, operationDbId);

        OperationalEventType evt = switch (op.getStatus()) {
            case COMPLETED -> OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_COMPLETED;
            case COMPLETED_WITH_NOOPS -> OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_COMPLETED;
            case PARTIAL_FAILED -> OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_PARTIAL_FAILED;
            case FAILED -> OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_FAILED;
            default -> OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_COMPLETED;
        };

        operationalEventLogService.logPublicEvent(
                op.getTenant(), null, op.getSyncSession().getUnidadeAtendimento(), null, null,
                evt,
                OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                op.getId(),
                OperationalOrigem.SYSTEM,
                "Offline replay operation finished",
                Map.of(
                        "tenantId", tenantId,
                        "serverSyncId", op.getServerSyncId(),
                        "operationId", op.getOperationId(),
                        "status", op.getStatus().name(),
                        "totalItems", op.getTotalItems(),
                        "succeededItems", op.getSucceededItems(),
                        "blockedItems", op.getBlockedItems(),
                        "failedItems", op.getFailedItems()
                ),
                null, null
        );
    }

    private List<Long> claimNextItemIds(Long tenantId,
                                       Long operationDbId,
                                       int limit,
                                       Instant now,
                                       Instant lockExpiredAt,
                                       String lockedBy) {
        try {
            return jdbcTemplate.queryForList("""
                    update device_offline_replay_operation_items
                       set item_status = 'RUNNING',
                           locked_at = ?,
                           locked_by = ?,
                           started_at = coalesce(started_at, ?)
                     where id in (
                         select id
                           from device_offline_replay_operation_items
                          where tenant_id = ?
                            and operation_db_id = ?
                            and item_status = 'PENDING'
                            and (next_retry_at is null or next_retry_at <= ?)
                            and (locked_at is null or locked_at < ?)
                          order by id asc
                          limit ?
                          for update skip locked
                     )
                    returning id
                    """, Long.class,
                    now, lockedBy, now,
                    tenantId, operationDbId,
                    now, lockExpiredAt,
                    limit
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private String workerId() {
        return "offline-replay-worker-" + UUID.randomUUID();
    }
}
