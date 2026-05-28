package com.restaurante.service.operacional;

import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.PedidoService;
import com.restaurante.service.tenantadmin.TenantAdminPedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PedidoStatusTransitionService {

    private final TenantGuard tenantGuard;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final PedidoService pedidoService;
    private final TenantAdminPedidoService tenantAdminPedidoService;
    private final OperationalEventLogService operationalEventLogService;

    /**
     * Atualiza status operacional do Pedido de forma segura.
     *
     * Nota:
     * - Pedido.status é derivado dos SubPedidos.
     * - Nesta fase, suportamos operação explícita para:
     *   - CANCELADO: cancela subpedidos (se permitido) e marca pedido como CANCELADO.
     *   - FINALIZADO: marca subpedidos PRONTO -> ENTREGUE e recalcula status do pedido.
     * - Demais estados são derivados automaticamente e auditados via eventos.
     */
    @Transactional
    public TenantPedidoDetalheResponse atualizarStatusPedido(Long pedidoId, StatusPedido novoStatus, String motivo, String ip, String userAgent) {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );

        if (novoStatus == null) throw new BusinessException("Status é obrigatório.");

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComItens(pedidoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        StatusPedido anterior = pedido.getStatus();

        if (novoStatus == StatusPedido.FINALIZADO) {
            // Para finalizar pedido, exige que todos os subpedidos estejam PRONTO (ou já ENTREGUE)
            List<SubPedido> subs = pedido.getSubPedidos() != null ? pedido.getSubPedidos() : List.of();
            for (SubPedido sp : subs) {
                if (sp.getStatus() == null) {
                    throw new ConflictException("Não é possível finalizar: existe subpedido sem status.");
                }
                if (sp.getStatus() == StatusSubPedido.ENTREGUE) continue;
                if (sp.getStatus() != StatusSubPedido.PRONTO) {
                    throw new ConflictException("Não é possível finalizar: existem subpedidos que não estão PRONTO.");
                }
            }

            var now = java.time.LocalDateTime.now();
            for (SubPedido sp : subs) {
                if (sp.getStatus() == StatusSubPedido.PRONTO) {
                    sp.setStatus(StatusSubPedido.ENTREGUE);
                    sp.setEntregueEm(now);
                    operationalEventLogService.logSubPedidoStatusChanged(
                            sp,
                            StatusSubPedido.PRONTO.name(),
                            StatusSubPedido.ENTREGUE.name(),
                            resolveOrigem(),
                            motivo,
                            Map.of("bulkDeliveredByPedidoId", pedido.getId()),
                            ip,
                            userAgent
                    );
                }
            }
            subPedidoRepository.saveAll(subs);

            // Recalcula status do pedido de forma centralizada
            var beforePedidoStatus = pedido.getStatus();
            pedidoService.recalcularStatusPedido(pedido.getId());
            Pedido after = pedidoRepository.findByIdAndTenantId(pedido.getId(), ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

            if (after.getStatus() != StatusPedido.FINALIZADO) {
                throw new ConflictException("Não foi possível finalizar o pedido: status derivado não é FINALIZADO.");
            }

            if (beforePedidoStatus != after.getStatus()) {
                operationalEventLogService.logPedidoStatusChanged(
                        after,
                        beforePedidoStatus != null ? beforePedidoStatus.name() : null,
                        after.getStatus().name(),
                        resolveOrigem(),
                        "Status derivado após entrega dos subpedidos",
                        Map.of("deliveredSubPedidos", subs.stream().map(SubPedido::getId).toList()),
                        ip,
                        userAgent
                );
            }

            return tenantAdminPedidoService.buscarDetalhe(pedidoId);
        }

        if (novoStatus != StatusPedido.CANCELADO) {
            operationalEventLogService.logTransitionBlocked(
                    OperationalEntityType.PEDIDO,
                    pedido.getId(),
                    resolveOrigem(),
                    "Status do Pedido é derivado de SubPedidos; use fluxo de produção/entrega.",
                    Map.of("requestedStatus", novoStatus.name()),
                    ip,
                    userAgent
            );
            throw new ConflictException("Status do pedido é derivado dos subpedidos. Ação não suportada.");
        }

        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo é obrigatório para cancelar pedido.");
        }

        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new ConflictException("Pedido pago não pode ser cancelado operacionalmente nesta fase.");
        }

        // Cancela subpedidos se possível
        List<SubPedido> subs = pedido.getSubPedidos() != null ? pedido.getSubPedidos() : List.of();
        for (SubPedido sp : subs) {
            if (sp.getStatus() == null) continue;
            if (sp.getStatus().isTerminal()) continue;
            if (!sp.podeTransicionarPara(StatusSubPedido.CANCELADO)) {
                throw new ConflictException("Não é possível cancelar: existe subpedido em estado que não permite cancelamento.");
            }
        }

        for (SubPedido sp : subs) {
            if (sp.getStatus() == null) continue;
            if (sp.getStatus().isTerminal()) continue;
            sp.setStatus(StatusSubPedido.CANCELADO);
        }
        subPedidoRepository.saveAll(subs);

        // Pedido.status será recalculado por PedidoService em fluxos existentes; aqui atualizamos diretamente para refletir cancelamento imediato
        pedido.setStatus(StatusPedido.CANCELADO);
        pedidoRepository.save(pedido);

        operationalEventLogService.logPedidoStatusChanged(
                pedido,
                anterior != null ? anterior.name() : null,
                pedido.getStatus().name(),
                resolveOrigem(),
                motivo,
                Map.of("cancelledSubPedidos", subs.stream().map(SubPedido::getId).toList()),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedidoId);
    }

    private OperationalOrigem resolveOrigem() {
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_CASHIER)) return OperationalOrigem.TENANT_CASHIER;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OPERATOR)) return OperationalOrigem.TENANT_OPERATOR;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_ADMIN)) return OperationalOrigem.TENANT_ADMIN;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER)) return OperationalOrigem.TENANT_OWNER;
        return OperationalOrigem.SYSTEM;
    }
}
