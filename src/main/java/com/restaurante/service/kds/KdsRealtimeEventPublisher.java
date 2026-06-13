package com.restaurante.service.kds;

import com.restaurante.dto.response.kds.KdsRealtimeEventResponse;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.KdsRealtimeEventType;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.SubPedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KdsRealtimeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final SubPedidoRepository subPedidoRepository;

    public void publishCreatedAfterCommit(SubPedido subPedido) {
        if (subPedido == null) {
            return;
        }
        publishAfterCommit(subPedido.getTenant() != null ? subPedido.getTenant().getId() : null,
                subPedido.getId(),
                null,
                subPedido.getStatus(),
                KdsRealtimeEventType.SUBPEDIDO_CREATED);
    }

    public void publishTransitionAfterCommit(Long tenantId,
                                             Long subPedidoId,
                                             StatusSubPedido statusAnterior,
                                             StatusSubPedido statusAtual,
                                             KdsRealtimeEventType eventType) {
        publishAfterCommit(tenantId, subPedidoId, statusAnterior, statusAtual, eventType);
    }

    private void publishAfterCommit(Long tenantId,
                                    Long subPedidoId,
                                    StatusSubPedido statusAnterior,
                                    StatusSubPedido statusAtual,
                                    KdsRealtimeEventType eventType) {
        if (tenantId == null || subPedidoId == null || eventType == null) {
            log.debug("Evento KDS ignorado por dados insuficientes: tenantId={}, subPedidoId={}, eventType={}",
                    tenantId, subPedidoId, eventType);
            return;
        }
        try {
            applicationEventPublisher.publishEvent(new KdsRealtimeDomainEvent(
                    tenantId, subPedidoId, statusAnterior, statusAtual, eventType));
        } catch (Exception e) {
            log.warn("Falha ao enfileirar evento KDS realtime: tenantId={}, subPedidoId={}, eventType={}, erro={}",
                    tenantId, subPedidoId, eventType, e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onKdsRealtimeDomainEvent(KdsRealtimeDomainEvent event) {
        try {
            SubPedido subPedido = subPedidoRepository.findKdsContractByIdAndTenant(event.subPedidoId(), event.tenantId())
                    .orElse(null);
            if (subPedido == null) {
                log.debug("Evento KDS realtime descartado: subPedido {} nao encontrado para tenant {}",
                        event.subPedidoId(), event.tenantId());
                return;
            }

            KdsRealtimeEventResponse payload = toResponse(event, subPedido);
            String tenantTopic = "/topic/tenant/%d/kds".formatted(event.tenantId());
            messagingTemplate.convertAndSend(tenantTopic, payload);

            if (payload.unidadeProducaoId() != null) {
                String unidadeTopic = "/topic/tenant/%d/kds/unidade/%d"
                        .formatted(event.tenantId(), payload.unidadeProducaoId());
                messagingTemplate.convertAndSend(unidadeTopic, payload);
            }
        } catch (Exception e) {
            log.warn("Falha ao publicar evento KDS realtime: tenantId={}, subPedidoId={}, eventType={}, erro={}",
                    event.tenantId(), event.subPedidoId(), event.eventType(), e.getMessage());
        }
    }

    private KdsRealtimeEventResponse toResponse(KdsRealtimeDomainEvent event, SubPedido subPedido) {
        Pedido pedido = subPedido.getPedido();
        var unidade = subPedido.getUnidadeProducao();
        return new KdsRealtimeEventResponse(
                UUID.randomUUID(),
                event.tenantId(),
                event.eventType(),
                subPedido.getId(),
                pedido != null ? pedido.getId() : null,
                pedido != null ? pedido.getNumero() : null,
                unidade != null ? unidade.getId() : null,
                unidade != null ? unidade.getNome() : null,
                event.statusAnterior(),
                subPedido.getStatus() != null ? subPedido.getStatus() : event.statusAtual(),
                subPedido.getVersion(),
                Instant.now()
        );
    }

    public record KdsRealtimeDomainEvent(
            Long tenantId,
            Long subPedidoId,
            StatusSubPedido statusAnterior,
            StatusSubPedido statusAtual,
            KdsRealtimeEventType eventType
    ) {
    }
}
