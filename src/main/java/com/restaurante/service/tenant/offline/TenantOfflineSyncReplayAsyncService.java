package com.restaurante.service.tenant.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.restaurante.dto.response.OfflineReplayAsyncSubmitResponse;
import com.restaurante.dto.response.OfflineReplayOperationItemResponse;
import com.restaurante.dto.response.OfflineReplayOperationResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;
import com.restaurante.model.enums.DeviceOfflineReplayRateLimitReason;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantOfflineSyncReplayAsyncService {

    private final TenantGuard tenantGuard;
    private final DeviceOfflineReplayAsyncProperties props;
    private final ObjectMapper objectMapper;
    private final DeviceOfflineSyncSessionRepository sessionRepository;
    private final DeviceOfflineCommandRepository commandRepository;
    private final DeviceOfflineReplayOperationRepository operationRepository;
    private final DeviceOfflineReplayOperationItemRepository itemRepository;
    private final DeviceOfflineReplayEligibilityService eligibilityService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public OfflineReplayAsyncSubmitResponse submit(String serverSyncId,
                                                   List<Long> commandIds,
                                                   List<DeviceOfflineCommandStatus> statuses,
                                                   List<DeviceOfflineCommandType> commandTypes,
                                                   String reason,
                                                   boolean force,
                                                   String ip,
                                                   String userAgent) {
        if (!props.isEnabled()) throw new BusinessException("OFFLINE_REPLAY_ASYNC_DISABLED");
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        if (reason == null || reason.isBlank()) throw new BusinessException("REPLAY_REASON_REQUIRED");

        DeviceOfflineSyncSession session = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), serverSyncId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", serverSyncId));

        applyRateLimitsOrThrow(session.getTenant(), ctx.tenantId(), serverSyncId, ip, userAgent);

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

        if (candidates.isEmpty()) throw new BusinessException("OFFLINE_REPLAY_NO_ELIGIBLE_COMMANDS");
        if (candidates.size() > props.getMaxItemsPerOperation()) throw new BusinessException("OFFLINE_REPLAY_TOO_MANY_ITEMS");

        List<DeviceOfflineCommand> selected = new ArrayList<>();
        List<OfflineCommandReplayEligibilityResponse> eligibilities = new ArrayList<>();
        for (DeviceOfflineCommand c : candidates) {
            OfflineCommandReplayEligibilityResponse e = eligibilityService.evaluate(c, false);
            boolean include = e.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.ELIGIBLE;
            if (!include && force && e.getEligibilityStatus() == DeviceOfflineReplayEligibilityStatus.REQUIRES_MANUAL_REVIEW) {
                include = true;
            }
            if (include) {
                selected.add(c);
                eligibilities.add(e);
            }
        }

        if (selected.isEmpty()) throw new BusinessException("OFFLINE_REPLAY_NO_ELIGIBLE_COMMANDS");

        String operationId = UUID.randomUUID().toString();
        DeviceOfflineReplayOperation op = new DeviceOfflineReplayOperation();
        op.setTenant(session.getTenant());
        op.setSyncSession(session);
        op.setServerSyncId(serverSyncId);
        op.setOperationId(operationId);
        op.setStatus(DeviceOfflineReplayOperationStatus.PENDING);
        op.setRequestedBy(ctx.userId());
        op.setRequestedAt(Instant.now());
        op.setReason(reason);
        op.setForce(force);
        op.setCommandStatusFilterJson(writeJsonSafe(statuses));
        op.setCommandTypeFilterJson(writeJsonSafe(commandTypes));
        op.setCommandIdsJson(writeJsonSafe(commandIds));
        op.setTotalItems(selected.size());
        op.setPendingItems(selected.size());
        op.setProgressPercent(0);
        operationRepository.save(op);

        List<Long> ids = selected.stream().map(DeviceOfflineCommand::getId).toList();
        int locked = commandRepository.markReplayInProgress(ctx.tenantId(), ids, op.getId());
        if (locked != ids.size()) {
            // não permite operação parcial ambígua no MVP
            throw new BusinessException("OFFLINE_REPLAY_COMMAND_ALREADY_IN_PROGRESS");
        }

        for (int i = 0; i < selected.size(); i++) {
            DeviceOfflineCommand cmd = selected.get(i);
            OfflineCommandReplayEligibilityResponse e = eligibilities.get(i);

            DeviceOfflineReplayOperationItem item = new DeviceOfflineReplayOperationItem();
            item.setTenant(cmd.getTenant());
            item.setOperation(op);
            item.setOperationId(op.getOperationId());
            item.setSyncSession(session);
            item.setServerSyncId(serverSyncId);
            item.setCommand(cmd);
            item.setClientRequestId(cmd.getClientRequestId());
            item.setCommandType(cmd.getCommandType() != null ? cmd.getCommandType().name() : null);
            item.setPreviousStatus(cmd.getStatus() != null ? cmd.getStatus().name() : null);
            item.setItemStatus(DeviceOfflineReplayOperationItemStatus.PENDING);
            item.setEligibilityStatus(e.getEligibilityStatus() != null ? e.getEligibilityStatus().name() : null);
            item.setEligibilityReason(e.getReason() != null ? e.getReason().name() : null);
            itemRepository.save(item);
        }

        operationalEventLogService.logPublicEvent(
                session.getTenant(), null, session.getUnidadeAtendimento(), null, null,
                OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_SUBMITTED,
                OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                op.getId(),
                OperationalOrigem.SYSTEM,
                "Offline replay operation submitted",
                Map.of(
                        "tenantId", ctx.tenantId(),
                        "serverSyncId", serverSyncId,
                        "operationId", op.getOperationId(),
                        "totalItems", op.getTotalItems(),
                        "force", force,
                        "actorUserId", ctx.userId()
                ),
                ip, userAgent
        );

        OfflineReplayAsyncSubmitResponse out = new OfflineReplayAsyncSubmitResponse();
        out.setOperationId(op.getOperationId());
        out.setServerSyncId(serverSyncId);
        out.setStatus(op.getStatus());
        out.setTotalItems(op.getTotalItems());
        out.setRequestedAt(op.getRequestedAt());
        out.setProgressPercent(op.getProgressPercent());
        return out;
    }

    @Transactional(readOnly = true)
    public OfflineReplayOperationResponse getOperation(String operationId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        DeviceOfflineReplayOperation op = operationRepository.findByTenant_IdAndOperationId(ctx.tenantId(), operationId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineReplayOperation", "operationId", operationId));

        return toResponse(op);
    }

    @Transactional(readOnly = true)
    public Page<OfflineReplayOperationItemResponse> listItems(String operationId,
                                                             List<DeviceOfflineReplayOperationItemStatus> statuses,
                                                             Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        boolean allStatuses = statuses == null || statuses.isEmpty();
        Page<DeviceOfflineReplayOperationItem> page = itemRepository.listByOperation(
                ctx.tenantId(),
                operationId,
                allStatuses ? List.of(DeviceOfflineReplayOperationItemStatus.PENDING) : statuses,
                allStatuses,
                pageable
        );
        return page.map(this::toItemResponse);
    }

    @Transactional
    public OfflineReplayOperationResponse rerun(String operationId,
                                               List<DeviceOfflineReplayOperationItemStatus> onlyStatuses,
                                               String reason,
                                               boolean force,
                                               String ip,
                                               String userAgent) {
        if (!props.isEnabled()) throw new BusinessException("OFFLINE_REPLAY_ASYNC_DISABLED");
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        if (reason == null || reason.isBlank()) throw new BusinessException("REPLAY_REASON_REQUIRED");

        DeviceOfflineReplayOperation op = operationRepository.findByTenant_IdAndOperationId(ctx.tenantId(), operationId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineReplayOperation", "operationId", operationId));
        if (op.getStatus() == DeviceOfflineReplayOperationStatus.RUNNING || op.getStatus() == DeviceOfflineReplayOperationStatus.PENDING) {
            throw new BusinessException("OFFLINE_REPLAY_OPERATION_RUNNING");
        }

        List<DeviceOfflineReplayOperationItemStatus> statuses = onlyStatuses != null && !onlyStatuses.isEmpty()
                ? onlyStatuses
                : List.of(DeviceOfflineReplayOperationItemStatus.FAILED, DeviceOfflineReplayOperationItemStatus.BLOCKED);

        List<DeviceOfflineReplayOperationItem> items = itemRepository.listByOperation(ctx.tenantId(), operationId, statuses, false, Pageable.unpaged()).getContent();
        if (items.isEmpty()) throw new BusinessException("OFFLINE_REPLAY_NO_REPROCESSABLE_ITEMS");

        int reset = 0;
        for (DeviceOfflineReplayOperationItem it : items) {
            if (it.getItemStatus() == DeviceOfflineReplayOperationItemStatus.SUCCEEDED || it.getItemStatus() == DeviceOfflineReplayOperationItemStatus.NOOP) continue;
            it.setItemStatus(DeviceOfflineReplayOperationItemStatus.PENDING);
            it.setAttempts(0);
            it.setNextRetryAt(null);
            it.setLockedAt(null);
            it.setLockedBy(null);
            it.setStartedAt(null);
            it.setFinishedAt(null);
            it.setErrorCode(null);
            it.setErrorMessage(null);
            it.setResultStatus(null);
            itemRepository.save(it);
            reset++;
        }

        // re-trava comandos para esta operação, garantindo que worker não concorra
        List<Long> cmdIds = items.stream().map(i -> i.getCommand().getId()).toList();
        commandRepository.markReplayInProgress(ctx.tenantId(), cmdIds, op.getId());

        op.setStatus(DeviceOfflineReplayOperationStatus.PENDING);
        op.setRetryCount(op.getRetryCount() + 1);
        op.setReason(reason);
        op.setForce(force);
        op.setLastError(null);
        op.setStartedAt(null);
        op.setFinishedAt(null);
        op.setLastProgressAt(null);
        // counters serão recalculados pelo worker, mas mantemos um snapshot coerente agora
        long pending = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.PENDING));
        long running = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.RUNNING));
        long succ = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.SUCCEEDED));
        long noop = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.NOOP));
        long blocked = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.BLOCKED));
        long failed = itemRepository.countByOperationAndStatuses(ctx.tenantId(), op.getId(), List.of(DeviceOfflineReplayOperationItemStatus.FAILED));
        op.setPendingItems((int) pending);
        op.setRunningItems((int) running);
        op.setSucceededItems((int) succ);
        op.setNoopItems((int) noop);
        op.setBlockedItems((int) blocked);
        op.setFailedItems((int) failed);
        int processed = (int) (succ + noop + blocked + failed);
        int pct = op.getTotalItems() <= 0 ? 100 : Math.min(100, (processed * 100) / op.getTotalItems());
        op.setProgressPercent(pct);
        operationRepository.save(op);

        operationalEventLogService.logPublicEvent(
                op.getTenant(), null, op.getSyncSession().getUnidadeAtendimento(), null, null,
                OperationalEventType.DEVICE_OFFLINE_REPLAY_OPERATION_RERUN_REQUESTED,
                OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                op.getId(),
                OperationalOrigem.SYSTEM,
                "Offline replay operation rerun requested",
                Map.of(
                        "tenantId", ctx.tenantId(),
                        "serverSyncId", op.getServerSyncId(),
                        "operationId", op.getOperationId(),
                        "resetItems", reset,
                        "force", force,
                        "actorUserId", ctx.userId()
                ),
                ip, userAgent
        );

        return toResponse(op);
    }

    private void applyRateLimitsOrThrow(com.restaurante.model.entity.Tenant tenant,
                                        Long tenantId,
                                        String serverSyncId,
                                        String ip,
                                        String userAgent) {
        Instant now = Instant.now();
        long active = operationRepository.countByTenantAndStatuses(tenantId, List.of(DeviceOfflineReplayOperationStatus.PENDING, DeviceOfflineReplayOperationStatus.RUNNING));
        if (active >= props.getMaxActiveOperationsPerTenant()) {
            auditRateLimited(tenant, tenantId, serverSyncId, DeviceOfflineReplayRateLimitReason.TENANT_ACTIVE_OPERATION_LIMIT, ip, userAgent);
            throw new BusinessException("OFFLINE_REPLAY_RATE_LIMITED");
        }
        long hour = operationRepository.countRequestedSince(tenantId, now.minus(1, ChronoUnit.HOURS));
        if (hour >= props.getMaxOperationsPerTenantPerHour()) {
            auditRateLimited(tenant, tenantId, serverSyncId, DeviceOfflineReplayRateLimitReason.TENANT_HOURLY_REPLAY_LIMIT, ip, userAgent);
            throw new BusinessException("OFFLINE_REPLAY_RATE_LIMITED");
        }
        boolean exists = operationRepository.existsByTenant_IdAndServerSyncIdAndStatusIn(tenantId, serverSyncId, List.of(DeviceOfflineReplayOperationStatus.PENDING, DeviceOfflineReplayOperationStatus.RUNNING));
        if (exists) {
            auditRateLimited(tenant, tenantId, serverSyncId, DeviceOfflineReplayRateLimitReason.OPERATION_ALREADY_RUNNING_FOR_SESSION, ip, userAgent);
            throw new BusinessException("OFFLINE_REPLAY_OPERATION_ALREADY_RUNNING");
        }
    }

    private void auditRateLimited(com.restaurante.model.entity.Tenant tenant,
                                  Long tenantId,
                                  String serverSyncId,
                                  DeviceOfflineReplayRateLimitReason reason,
                                  String ip,
                                  String userAgent) {
        try {
            operationalEventLogService.logPublicEvent(
                    tenant, null, null, null, null,
                    OperationalEventType.DEVICE_OFFLINE_REPLAY_RATE_LIMITED,
                    OperationalEntityType.DEVICE_OFFLINE_REPLAY_OPERATION,
                    null,
                    OperationalOrigem.SYSTEM,
                    "Offline replay rate-limited",
                    Map.of(
                            "tenantId", tenantId,
                            "serverSyncId", serverSyncId,
                            "rateLimitReason", reason.name()
                    ),
                    ip, userAgent
            );
        } catch (Exception ignored) {
        }
    }

    private OfflineReplayOperationResponse toResponse(DeviceOfflineReplayOperation op) {
        OfflineReplayOperationResponse out = new OfflineReplayOperationResponse();
        out.setOperationId(op.getOperationId());
        out.setServerSyncId(op.getServerSyncId());
        out.setStatus(op.getStatus());
        out.setTotalItems(op.getTotalItems());
        out.setPendingItems(op.getPendingItems());
        out.setRunningItems(op.getRunningItems());
        out.setSucceededItems(op.getSucceededItems());
        out.setNoopItems(op.getNoopItems());
        out.setBlockedItems(op.getBlockedItems());
        out.setFailedItems(op.getFailedItems());
        out.setProgressPercent(op.getProgressPercent());
        out.setRequestedAt(op.getRequestedAt());
        out.setStartedAt(op.getStartedAt());
        out.setFinishedAt(op.getFinishedAt());
        out.setLastProgressAt(op.getLastProgressAt());
        out.setReason(op.getReason());
        out.setRequestedBy(op.getRequestedBy());
        return out;
    }

    private OfflineReplayOperationItemResponse toItemResponse(DeviceOfflineReplayOperationItem it) {
        OfflineReplayOperationItemResponse out = new OfflineReplayOperationItemResponse();
        out.setItemId(it.getId());
        out.setCommandId(it.getCommand() != null ? it.getCommand().getId() : null);
        out.setClientRequestId(it.getClientRequestId());
        out.setCommandType(it.getCommandType());
        out.setPreviousStatus(it.getPreviousStatus());
        out.setItemStatus(it.getItemStatus());
        out.setEligibilityStatus(it.getEligibilityStatus());
        out.setEligibilityReason(it.getEligibilityReason());
        out.setReplayAttemptId(it.getReplayAttempt() != null ? it.getReplayAttempt().getId() : null);
        out.setResultStatus(it.getResultStatus());
        out.setErrorCode(it.getErrorCode());
        out.setErrorMessage(it.getErrorMessage());
        out.setAttempts(it.getAttempts());
        out.setNextRetryAt(it.getNextRetryAt());
        out.setStartedAt(it.getStartedAt());
        out.setFinishedAt(it.getFinishedAt());
        return out;
    }

    private String writeJsonSafe(Object value) {
        try {
            if (value == null) return null;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
