package com.restaurante.service.tenant.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.config.DeviceOfflineSyncObservabilityProperties;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.entity.DeviceOfflineSyncSession;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.device.offline.repository.DeviceOfflineSyncSessionRepository;
import com.restaurante.dto.response.TenantOfflineSyncCommandSummaryResponse;
import com.restaurante.dto.response.TenantOfflineSyncMetricsResponse;
import com.restaurante.dto.response.TenantOfflineSyncSessionDetailResponse;
import com.restaurante.dto.response.TenantOfflineSyncSessionListItemResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantOfflineSyncObservabilityService {

    private final TenantGuard tenantGuard;
    private final DeviceOfflineSyncObservabilityProperties props;
    private final DeviceOfflineSyncSessionRepository sessionRepository;
    private final DeviceOfflineCommandRepository commandRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<TenantOfflineSyncSessionListItemResponse> listSessions(
            Long unidadeId,
            Long deviceId,
            String status,
            String appVersion,
            Instant dateFrom,
            Instant dateTo,
            Boolean hasFailures,
            Boolean hasConflicts,
            Boolean hasRejected,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        QueryWindow window = normalizeWindow(dateFrom, dateTo);
        Pageable effective = normalizePageable(pageable, Sort.by(Sort.Direction.DESC, "receivedAt"));

        Specification<DeviceOfflineSyncSession> spec = (root, query, cb) -> cb.equal(root.get("tenant").get("id"), ctx.tenantId());
        if (unidadeId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("unidadeAtendimento").get("id"), unidadeId));
        if (deviceId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("dispositivoOperacional").get("id"), deviceId));
        if (status != null && !status.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), Enum.valueOf(com.restaurante.model.enums.DeviceOfflineSyncSessionStatus.class, status)));
        if (appVersion != null && !appVersion.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("appVersion"), appVersion));
        spec = spec.and((r, q, cb) -> cb.between(r.get("receivedAt"), window.from(), window.to()));
        if (Boolean.TRUE.equals(hasFailures)) spec = spec.and((r, q, cb) -> cb.greaterThan(r.get("failedCount"), 0));
        if (Boolean.TRUE.equals(hasConflicts)) spec = spec.and((r, q, cb) -> cb.greaterThan(r.get("conflictCount"), 0));
        if (Boolean.TRUE.equals(hasRejected)) spec = spec.and((r, q, cb) -> cb.greaterThan(r.get("rejectedCount"), 0));

        return sessionRepository.findAll(spec, effective).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public TenantOfflineSyncSessionDetailResponse getSession(String serverSyncId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        DeviceOfflineSyncSession s = sessionRepository.findByTenantIdAndServerSyncId(ctx.tenantId(), serverSyncId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceOfflineSyncSession", "serverSyncId", serverSyncId));

        if (props.isAuditDetailView()) {
            operationalEventLogService.logPublicEvent(
                    s.getTenant(), null, s.getUnidadeAtendimento(), null, null,
                    OperationalEventType.DEVICE_OFFLINE_SYNC_TROUBLESHOOTING_VIEWED,
                    OperationalEntityType.DEVICE_OFFLINE_SYNC,
                    s.getId(),
                    OperationalOrigem.SYSTEM,
                    "Offline sync troubleshooting viewed",
                    Map.of("tenantId", ctx.tenantId(), "serverSyncId", s.getServerSyncId(), "actorUserId", ctx.userId(), "actorType", "TENANT_USER"),
                    ip, userAgent
            );
        }

        return toDetail(s);
    }

    @Transactional(readOnly = true)
    public Page<TenantOfflineSyncCommandSummaryResponse> listCommands(
            String serverSyncId,
            DeviceOfflineCommandStatus status,
            DeviceOfflineCommandType commandType,
            String conflictCode,
            String errorCode,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        Pageable effective = normalizePageable(pageable, Sort.by(Sort.Direction.ASC, "commandIndex"));
        return commandRepository.searchBySession(ctx.tenantId(), serverSyncId, status, commandType, conflictCode, errorCode, effective)
                .map(this::toCommandSummary);
    }

    @Transactional(readOnly = true)
    public TenantOfflineSyncMetricsResponse metrics(Long unidadeId, Long deviceId, Instant dateFrom, Instant dateTo) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        QueryWindow window = normalizeWindow(dateFrom, dateTo);

        // totals via sessions (mais barato que iterar commands)
        Specification<DeviceOfflineSyncSession> spec = (root, query, cb) -> cb.equal(root.get("tenant").get("id"), ctx.tenantId());
        if (unidadeId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("unidadeAtendimento").get("id"), unidadeId));
        if (deviceId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("dispositivoOperacional").get("id"), deviceId));
        spec = spec.and((r, q, cb) -> cb.between(r.get("receivedAt"), window.from(), window.to()));

        List<DeviceOfflineSyncSession> sessions = sessionRepository.findAll(spec);

        long totalSessions = sessions.size();
        long totalCommands = sessions.stream().mapToLong(DeviceOfflineSyncSession::getTotalCommands).sum();
        long applied = sessions.stream().mapToLong(DeviceOfflineSyncSession::getAppliedCount).sum();
        long dup = sessions.stream().mapToLong(DeviceOfflineSyncSession::getDuplicateCount).sum();
        long rej = sessions.stream().mapToLong(DeviceOfflineSyncSession::getRejectedCount).sum();
        long conf = sessions.stream().mapToLong(DeviceOfflineSyncSession::getConflictCount).sum();
        long fail = sessions.stream().mapToLong(DeviceOfflineSyncSession::getFailedCount).sum();
        Long avgDuration = totalSessions == 0 ? null : Math.round(sessions.stream().filter(s -> s.getDurationMs() != null).mapToLong(DeviceOfflineSyncSession::getDurationMs).average().orElse(0));

        Map<String, Long> topConflicts = toCountMap(commandRepository.topConflictCodes(ctx.tenantId(), window.from(), window.to(), unidadeId, deviceId, PageRequest.of(0, 10)));
        Map<String, Long> topErrors = toCountMap(commandRepository.topErrorCodes(ctx.tenantId(), window.from(), window.to(), unidadeId, deviceId, PageRequest.of(0, 10)));
        Map<String, Long> appVersions = toCountMap(sessionRepository.countByAppVersion(ctx.tenantId(), window.from(), window.to(), unidadeId, deviceId));

        List<Object[]> rankingRaw = commandRepository.deviceFailureRanking(ctx.tenantId(), window.from(), window.to(), unidadeId, deviceId, PageRequest.of(0, 10));
        List<TenantOfflineSyncMetricsResponse.DeviceFailureRankItem> ranking = rankingRaw.stream().map(arr -> {
            TenantOfflineSyncMetricsResponse.DeviceFailureRankItem it = new TenantOfflineSyncMetricsResponse.DeviceFailureRankItem();
            it.setDeviceId(((Number) arr[0]).longValue());
            it.setDeviceName(String.valueOf(arr[1]));
            it.setFailedCount(((Number) arr[2]).longValue());
            return it;
        }).toList();

        TenantOfflineSyncMetricsResponse out = new TenantOfflineSyncMetricsResponse();
        out.setTotalSessions(totalSessions);
        out.setTotalCommands(totalCommands);
        out.setAppliedCommands(applied);
        out.setDuplicateCommands(dup);
        out.setRejectedCommands(rej);
        out.setConflictCommands(conf);
        out.setFailedCommands(fail);
        out.setAverageDurationMs(avgDuration);
        out.setTopConflictCodes(topConflicts);
        out.setTopErrorCodes(topErrors);
        out.setAppVersionBreakdown(appVersions);
        out.setDeviceFailureRanking(ranking);
        return out;
    }

    private TenantOfflineSyncSessionListItemResponse toListItem(DeviceOfflineSyncSession s) {
        TenantOfflineSyncSessionListItemResponse r = new TenantOfflineSyncSessionListItemResponse();
        r.setServerSyncId(s.getServerSyncId());
        r.setSyncSessionId(s.getSyncSessionId());
        r.setUnidadeId(s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getId() : null);
        r.setUnidadeNome(s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getNome() : null);
        r.setDeviceId(s.getDispositivoOperacional() != null ? s.getDispositivoOperacional().getId() : null);
        r.setDeviceName(s.getDispositivoOperacional() != null ? s.getDispositivoOperacional().getNome() : null);
        r.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        r.setAppVersion(s.getAppVersion());
        r.setReceivedAt(s.getReceivedAt());
        r.setDurationMs(s.getDurationMs());
        r.setTotalCommands(s.getTotalCommands());
        r.setAppliedCount(s.getAppliedCount());
        r.setDuplicateCount(s.getDuplicateCount());
        r.setRejectedCount(s.getRejectedCount());
        r.setConflictCount(s.getConflictCount());
        r.setFailedCount(s.getFailedCount());
        return r;
    }

    private TenantOfflineSyncSessionDetailResponse toDetail(DeviceOfflineSyncSession s) {
        TenantOfflineSyncSessionDetailResponse r = new TenantOfflineSyncSessionDetailResponse();
        r.setServerSyncId(s.getServerSyncId());
        r.setSyncSessionId(s.getSyncSessionId());
        r.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        r.setAppVersion(s.getAppVersion());
        r.setUnidadeId(s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getId() : null);
        r.setUnidadeNome(s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getNome() : null);
        r.setDeviceId(s.getDispositivoOperacional() != null ? s.getDispositivoOperacional().getId() : null);
        r.setDeviceName(s.getDispositivoOperacional() != null ? s.getDispositivoOperacional().getNome() : null);
        r.setReceivedAt(s.getReceivedAt());
        r.setStartedProcessingAt(s.getStartedProcessingAt());
        r.setFinishedProcessingAt(s.getFinishedProcessingAt());
        r.setDurationMs(s.getDurationMs());
        r.setTotalCommands(s.getTotalCommands());
        r.setAppliedCount(s.getAppliedCount());
        r.setDuplicateCount(s.getDuplicateCount());
        r.setRejectedCount(s.getRejectedCount());
        r.setConflictCount(s.getConflictCount());
        r.setFailedCount(s.getFailedCount());
        r.setTotalPayloadBytes(s.getTotalPayloadBytes());
        r.setMaxCommandPayloadBytes(s.getMaxCommandPayloadBytes());
        r.setLocalRefCount(s.getLocalRefCount());
        r.setSummary(safeReadTree(s.getSummaryJson()));
        r.setErrorSummary(safeReadTree(s.getErrorSummaryJson()));
        return r;
    }

    private TenantOfflineSyncCommandSummaryResponse toCommandSummary(DeviceOfflineCommand c) {
        TenantOfflineSyncCommandSummaryResponse r = new TenantOfflineSyncCommandSummaryResponse();
        r.setClientRequestId(c.getClientRequestId());
        r.setCommandType(c.getCommandType());
        r.setStatus(c.getStatus());
        r.setCommandIndex(c.getCommandIndex());
        r.setPayloadSizeBytes(c.getPayloadSizeBytes());
        r.setDependsOnClientRequestId(c.getDependsOnClientRequestId());
        r.setDependencyStatus(c.getDependencyStatus());
        r.setCreatedEntityType(c.getCreatedEntityType());
        r.setCreatedEntityId(c.getCreatedEntityId());
        r.setErrorCode(c.getErrorCode());
        r.setConflictCode(c.getConflictCode());
        return r;
    }

    private QueryWindow normalizeWindow(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant effectiveTo = to != null ? to : now;
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofDays(7));

        long days = Math.abs(Duration.between(effectiveFrom, effectiveTo).toDays());
        if (days > props.getMaxQueryDays()) {
            effectiveFrom = effectiveTo.minus(Duration.ofDays(props.getMaxQueryDays()));
        }
        return new QueryWindow(effectiveFrom, effectiveTo);
    }

    private Pageable normalizePageable(Pageable pageable, Sort defaultSort) {
        int size = pageable != null ? pageable.getPageSize() : props.getDefaultPageSize();
        int page = pageable != null ? pageable.getPageNumber() : 0;
        if (size <= 0) size = props.getDefaultPageSize();
        if (size > props.getMaxPageSize()) size = props.getMaxPageSize();
        Sort sort = pageable != null && pageable.getSort() != null && pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        return PageRequest.of(page, size, sort);
    }

    private record QueryWindow(Instant from, Instant to) {}

    private JsonNode safeReadTree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (rows == null) return out;
        for (Object[] arr : rows) {
            out.put(String.valueOf(arr[0]), ((Number) arr[1]).longValue());
        }
        return out;
    }
}

