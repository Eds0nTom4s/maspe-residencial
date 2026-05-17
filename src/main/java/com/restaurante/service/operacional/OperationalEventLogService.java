package com.restaurante.service.operacional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalEventLogService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final OperationalEventLogRepository operationalEventLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void logPedidoStatusChanged(Pedido pedido,
                                      String statusAnterior,
                                      String statusNovo,
                                      OperationalOrigem origem,
                                      String motivo,
                                      Map<String, Object> metadata,
                                      String ip,
                                      String userAgent) {
        log(
                OperationalEventType.PEDIDO_STATUS_CHANGED,
                OperationalEntityType.PEDIDO,
                pedido != null ? pedido.getId() : null,
                pedido,
                null,
                statusAnterior,
                statusNovo,
                origem,
                motivo,
                metadata,
                ip,
                userAgent
        );
    }

    @Transactional
    public void logSubPedidoStatusChanged(SubPedido subPedido,
                                         String statusAnterior,
                                         String statusNovo,
                                         OperationalOrigem origem,
                                         String motivo,
                                         Map<String, Object> metadata,
                                         String ip,
                                         String userAgent) {
        log(
                OperationalEventType.SUBPEDIDO_STATUS_CHANGED,
                OperationalEntityType.SUBPEDIDO,
                subPedido != null ? subPedido.getId() : null,
                subPedido != null ? subPedido.getPedido() : null,
                subPedido,
                statusAnterior,
                statusNovo,
                origem,
                motivo,
                metadata,
                ip,
                userAgent
        );
    }

    @Transactional
    public void logTransitionBlocked(OperationalEntityType entityType,
                                    Long entityId,
                                    OperationalOrigem origem,
                                    String message,
                                    Map<String, Object> metadata,
                                    String ip,
                                    String userAgent) {
        log(
                OperationalEventType.TRANSITION_BLOCKED,
                entityType,
                entityId,
                null,
                null,
                null,
                null,
                origem,
                message,
                metadata,
                ip,
                userAgent
        );
    }

    private void log(OperationalEventType eventType,
                     OperationalEntityType entityType,
                     Long entityId,
                     Pedido pedido,
                     SubPedido subPedido,
                     String statusAnterior,
                     String statusNovo,
                     OperationalOrigem origem,
                     String motivo,
                     Map<String, Object> metadata,
                     String ip,
                     String userAgent) {

        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        OperationalEventLog log = new OperationalEventLog();
        log.setTenant(tenant);

        if (pedido != null && pedido.getSessaoConsumo() != null) {
            log.setInstituicao(pedido.getSessaoConsumo().getInstituicao());
            log.setUnidadeAtendimento(pedido.getSessaoConsumo().getUnidadeAtendimento());
            log.setMesa(pedido.getSessaoConsumo().getMesa());
        }

        log.setPedido(pedido);
        log.setSubPedido(subPedido);
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId != null ? entityId : 0L);
        log.setStatusAnterior(statusAnterior);
        log.setStatusNovo(statusNovo);
        log.setOrigem(origem != null ? origem : OperationalOrigem.SYSTEM);
        log.setMotivo(motivo);
        log.setMetadataJson(metadata != null ? toJson(metadata) : null);
        log.setIp(ip);
        log.setUserAgent(userAgent);

        // actor
        if (ctx.userId() != null) {
            User user = userRepository.findById(ctx.userId()).orElse(null);
            log.setActorUser(user);
            log.setActorType(OperationalActorType.USER);
        } else {
            log.setActorType(OperationalActorType.SYSTEM);
        }

        operationalEventLogRepository.save(log);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"metadata_json_serialization_failed\"}";
        }
    }
}

