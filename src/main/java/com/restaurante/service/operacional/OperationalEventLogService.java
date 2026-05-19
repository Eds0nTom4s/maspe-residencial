package com.restaurante.service.operacional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalEventLogService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
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

    @Transactional
    public void logTurnoEvent(OperationalEventType eventType,
                              TurnoOperacional turno,
                              OperationalOrigem origem,
                              String motivo,
                              Map<String, Object> metadata,
                              String ip,
                              String userAgent) {
        if (turno == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        logForTurno(
                eventType,
                OperationalEntityType.TURNO_OPERACIONAL,
                turno.getId(),
                turno,
                origem,
                motivo,
                metadata,
                ip,
                userAgent
        );
    }

    @Transactional
    public void logChecklistEvent(OperationalEventType eventType,
                                  TurnoOperacional turno,
                                  Long checklistRunId,
                                  OperationalOrigem origem,
                                  String motivo,
                                  Map<String, Object> metadata,
                                  String ip,
                                  String userAgent) {
        if (turno == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        logForTurno(
                eventType,
                OperationalEntityType.CHECKLIST_OPERACIONAL,
                checklistRunId != null ? checklistRunId : 0L,
                turno,
                origem,
                motivo,
                metadata,
                ip,
                userAgent
        );
    }

    @Transactional
    public void logPedidoSemTurnoAberto(Pedido pedido,
                                        OperationalOrigem origem,
                                        String motivo,
                                        Map<String, Object> metadata,
                                        String ip,
                                        String userAgent) {
        if (pedido == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        log(
                OperationalEventType.PEDIDO_SEM_TURNO_ABERTO,
                OperationalEntityType.PEDIDO,
                pedido.getId(),
                pedido,
                null,
                pedido.getStatus() != null ? pedido.getStatus().name() : null,
                pedido.getStatus() != null ? pedido.getStatus().name() : null,
                origem,
                motivo,
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
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            tenantId = resolveTenantIdFallbackFromPedido(pedido);
        }
        if (tenantId == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        Tenant tenant = tenantRepository.findById(tenantId)
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

        applyActor(log, origem, ip, userAgent);

        operationalEventLogRepository.save(log);
    }

    private void logForTurno(OperationalEventType eventType,
                             OperationalEntityType entityType,
                             Long entityId,
                             TurnoOperacional turno,
                             OperationalOrigem origem,
                             String motivo,
                             Map<String, Object> metadata,
                             String ip,
                             String userAgent) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        OperationalEventLog log = new OperationalEventLog();
        log.setTenant(tenant);
        log.setInstituicao(turno.getInstituicao());
        log.setUnidadeAtendimento(turno.getUnidadeAtendimento());
        log.setTurno(turno);

        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId != null ? entityId : 0L);
        log.setOrigem(origem != null ? origem : OperationalOrigem.SYSTEM);
        log.setMotivo(motivo);
        log.setMetadataJson(metadata != null ? toJson(metadata) : null);
        log.setIp(ip);
        log.setUserAgent(userAgent);

        applyActor(log, origem, ip, userAgent);

        operationalEventLogRepository.save(log);
    }

    private Long resolveTenantId() {
        try {
            TenantContext ctx = TenantContextHolderSafe.get();
            if (ctx != null && ctx.tenantId() != null) return ctx.tenantId();
        } catch (Exception ignored) { }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp) {
            return dp.tenantId();
        }
        return null;
    }

    private Long resolveTenantIdFallbackFromPedido(Pedido pedido) {
        try {
            if (pedido != null && pedido.getTenant() != null && pedido.getTenant().getId() != null) {
                return pedido.getTenant().getId();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void applyActor(OperationalEventLog log, OperationalOrigem origem, String ip, String userAgent) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;

        if (principal instanceof DevicePrincipal dp) {
            log.setActorType(OperationalActorType.DEVICE);
            log.setDispositivo(dispositivoOperacionalRepository.getReferenceById(dp.dispositivoId()));
            if (origem == null) {
                log.setOrigem(resolveOrigemFromDevice(dp.tipo()));
            }
            return;
        }

        TenantContext ctx = TenantContextHolderSafe.get();
        if (ctx != null && ctx.userId() != null) {
            User user = userRepository.findById(ctx.userId()).orElse(null);
            log.setActorUser(user);
            log.setActorType(OperationalActorType.USER);
            return;
        }

        log.setActorType(OperationalActorType.SYSTEM);
        if (origem == null) {
            log.setOrigem(OperationalOrigem.SYSTEM);
        }
    }

    private OperationalOrigem resolveOrigemFromDevice(DispositivoTipo tipo) {
        if (tipo == null) return OperationalOrigem.SYSTEM;
        return switch (tipo) {
            case POS, CHECKOUT, ADMIN, QUIOSQUE, BALCAO, OUTRO -> OperationalOrigem.DEVICE_POS;
            case KDS, COZINHA, BAR -> OperationalOrigem.DEVICE_KDS;
        };
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"metadata_json_serialization_failed\"}";
        }
    }
}

/**
 * Evita dependência forte do TenantGuard (que assume userId) para requests device-auth.
 */
final class TenantContextHolderSafe {
    private TenantContextHolderSafe() {}
    static TenantContext get() {
        try {
            return com.restaurante.security.tenant.TenantContextHolder.get().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
