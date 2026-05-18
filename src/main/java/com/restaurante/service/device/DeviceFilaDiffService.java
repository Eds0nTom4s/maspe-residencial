package com.restaurante.service.device;

import com.restaurante.dto.response.DeviceFilaDiffSyncResponse;
import com.restaurante.dto.response.DeviceFilaEventSyncItem;
import com.restaurante.dto.response.KdsSubPedidoResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.producao.ProducaoKdsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceFilaDiffService {

    private final OperationalEventLogRepository operationalEventLogRepository;
    private final ProducaoKdsService producaoKdsService;

    public record DiffResult(DeviceFilaDiffSyncResponse data,
                             boolean fullSyncRequired,
                             com.restaurante.dto.response.SyncEnvelope.FullSyncRequiredReason fullSyncReason,
                             List<com.restaurante.dto.response.SyncEnvelope.SyncWarning> warnings) {}

    @Value("${consuma.sync.fila.diff.default-limit:200}")
    private int defaultLimit;

    @Value("${consuma.sync.fila.diff.max-limit:1000}")
    private int maxLimit;

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new com.restaurante.exception.DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
    }

    @Transactional(readOnly = true)
    public DiffResult diff(DevicePrincipal device, Long sinceEventId, Integer limit) {
        requireCapability(device, DeviceCapability.VIEW_PRODUCTION);
        if (device.unidadeProducaoId() == null) {
            throw new ConflictException("DEVICE_PRODUCTION_UNIT_AMBIGUOUS");
        }

        Long tenantId = device.tenantId();
        Long unidadeProducaoId = device.unidadeProducaoId();
        LocalDateTime now = LocalDateTime.now();

        int effectiveLimit = limit == null ? defaultLimit : Math.min(Math.max(limit, 1), maxLimit);

        Long baseline = sinceEventId != null ? sinceEventId : 0L;
        Collection<OperationalEventType> types = List.of(OperationalEventType.SUBPEDIDO_STATUS_CHANGED, OperationalEventType.PEDIDO_STATUS_CHANGED);

        boolean fullSyncRequired = false;
        var fullSyncReason = com.restaurante.dto.response.SyncEnvelope.FullSyncRequiredReason.NONE;
        List<com.restaurante.dto.response.SyncEnvelope.SyncWarning> warnings = List.of();

        if (baseline > 0) {
            boolean exists = operationalEventLogRepository.existsByIdAndTenantAndUnidadeProducao(baseline, tenantId, unidadeProducaoId);
            if (!exists) {
                fullSyncRequired = true;
                fullSyncReason = com.restaurante.dto.response.SyncEnvelope.FullSyncRequiredReason.VERSION_MISMATCH;
                warnings = List.of(new com.restaurante.dto.response.SyncEnvelope.SyncWarning(
                        com.restaurante.dto.response.SyncEnvelope.SyncWarningCode.PARTIAL_RESPONSE,
                        "sinceEventId não encontrado no escopo; recomendado full sync."
                ));
                Long maxId = operationalEventLogRepository.maxIdByTenantAndUnidadeProducao(tenantId, unidadeProducaoId);
                DeviceFilaDiffSyncResponse resp = new DeviceFilaDiffSyncResponse(now, maxId, false, List.of(), List.of(), List.of());
                return new DiffResult(resp, true, fullSyncReason, warnings);
            }
        }

        long totalAfter = operationalEventLogRepository.countFilaEventsAfter(tenantId, unidadeProducaoId, baseline, types);
        boolean hasMore = totalAfter > effectiveLimit;

        List<OperationalEventLog> events = operationalEventLogRepository.findFilaEventsAfter(
                tenantId, unidadeProducaoId, baseline, types, PageRequest.of(0, effectiveLimit)
        );

        Long lastEventId = events.isEmpty()
                ? operationalEventLogRepository.maxIdByTenantAndUnidadeProducao(tenantId, unidadeProducaoId)
                : events.get(events.size() - 1).getId();

        List<DeviceFilaEventSyncItem> mappedEvents = events.stream()
                .map(e -> new DeviceFilaEventSyncItem(
                        e.getId(),
                        e.getEventType(),
                        e.getEntityType(),
                        e.getEntityId(),
                        e.getPedido() != null ? e.getPedido().getId() : null,
                        e.getSubPedido() != null ? e.getSubPedido().getId() : null,
                        e.getStatusAnterior(),
                        e.getStatusNovo(),
                        e.getOrigem(),
                        e.getCreatedAt()
                ))
                .toList();

        Set<Long> affected = events.stream()
                .map(e -> e.getSubPedido() != null ? e.getSubPedido().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        List<KdsSubPedidoResponse> subPedidosAtualizados = affected.isEmpty()
                ? List.of()
                : producaoKdsService.fetchKdsByIds(affected.stream().toList());

        DeviceFilaDiffSyncResponse resp = new DeviceFilaDiffSyncResponse(
                now,
                lastEventId,
                hasMore,
                mappedEvents,
                affected.stream().sorted().toList(),
                subPedidosAtualizados
        );
        return new DiffResult(resp, fullSyncRequired, fullSyncReason, warnings);
    }
}
