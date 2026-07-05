package com.restaurante.service.operacional;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.TurnoObrigatorioException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
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
    private final OperacaoProperties operacaoProperties;
    private final OperationalTemplatePolicy operationalTemplatePolicy;

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
        if (novoStatus == null) throw new BusinessException("Status é obrigatório.");

        TenantContext ctx = requireTenantCommandContext();
        Pedido pedido = loadPedido(ctx, pedidoId);
        validarTurnoObrigatorio(pedido);

        if (novoStatus == StatusPedido.FINALIZADO) {
            return finalizarPedido(ctx, pedido, motivo, ip, userAgent);
        }

        if (novoStatus != StatusPedido.CANCELADO) {
            operationalEventLogService.logTransitionBlocked(
                    OperationalEntityType.PEDIDO,
                    pedido.getId(),
                    resolveOrigem(),
                    "Status do Pedido é derivado de SubPedidos; use fluxo de produção/entrega.",
                    Map.of(
                            "requestedStatus", novoStatus.name(),
                            "tenantTemplate", resolveTenantTemplateCode(pedido),
                            "pedidoOrigem", resolvePedidoOrigem(pedido)
                    ),
                    ip,
                    userAgent
            );
            throw new ConflictException("Status do pedido é derivado dos subpedidos. Ação não suportada.");
        }

        return cancelarPedido(pedido, motivo, ip, userAgent, false);
    }

    /**
     * Comando explícito de aceite operacional.
     *
     * Aceitar não confirma pagamento e não inicia produção. O comando apenas
     * libera subpedidos CRIADO para PENDENTE; o status do Pedido continua
     * derivado pelo PedidoService.
     */
    @Transactional
    public TenantPedidoDetalheResponse aceitarPedido(Long pedidoId, String ip, String userAgent) {
        TenantContext ctx = requireTenantCommandContext();
        Pedido pedido = loadPedido(ctx, pedidoId);
        validarTurnoObrigatorio(pedido);
        operationalTemplatePolicy.assertCanAcceptPedido(pedido, resolveOrigem());

        StatusPedido statusAnterior = pedido.getStatus();
        StatusFinanceiroPedido financeiroAnterior = pedido.getStatusFinanceiro();
        if (statusAnterior != StatusPedido.CRIADO) {
            logPedidoCommandBlocked(
                    pedido,
                    "ACCEPT_ORDER",
                    "Pedido só pode ser aceite quando está CRIADO.",
                    ip,
                    userAgent
            );
            throw new ConflictException("Pedido só pode ser aceite quando está CRIADO.");
        }

        List<SubPedido> subs = getSubPedidosOrThrow(pedido);
        for (SubPedido sp : subs) {
            if (sp.getStatus() != StatusSubPedido.CRIADO) {
                logPedidoCommandBlocked(
                        pedido,
                        "ACCEPT_ORDER",
                        "Pedido possui subpedidos que não estão CRIADO.",
                        ip,
                        userAgent
                );
                throw new ConflictException("Pedido só pode ser aceite quando todos os subpedidos estão CRIADO.");
            }
        }

        for (SubPedido sp : subs) {
            sp.setStatus(StatusSubPedido.PENDENTE);
            operationalEventLogService.logSubPedidoStatusChanged(
                    sp,
                    StatusSubPedido.CRIADO.name(),
                    StatusSubPedido.PENDENTE.name(),
                    resolveOrigem(),
                    "Pedido aceite pelo operador",
                    Map.of(
                            "command", "ACCEPT_ORDER",
                            "paymentStatusPreserved", financeiroAnterior != null ? financeiroAnterior.name() : "UNKNOWN",
                            "productionStarted", false,
                            "tenantTemplate", resolveTenantTemplateCode(pedido),
                            "pedidoOrigem", resolvePedidoOrigem(pedido)
                    ),
                    ip,
                    userAgent
            );
        }
        subPedidoRepository.saveAll(subs);

        pedidoService.recalcularStatusPedido(pedido.getId());
        Pedido after = pedidoRepository.findByIdAndTenantIdComItens(pedido.getId(), ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (after.getStatusFinanceiro() != financeiroAnterior) {
            throw new ConflictException("Aceitar pedido não pode alterar o estado financeiro.");
        }
        if (after.getStatus() != StatusPedido.EM_ANDAMENTO) {
            throw new ConflictException("Não foi possível aceitar o pedido: status derivado não é EM_ANDAMENTO.");
        }

        operationalEventLogService.logPedidoStatusChanged(
                after,
                statusAnterior != null ? statusAnterior.name() : null,
                after.getStatus().name(),
                resolveOrigem(),
                "Pedido aceite pelo operador",
                Map.of(
                        "command", "ACCEPT_ORDER",
                        "acceptedSubPedidos", subs.stream().map(SubPedido::getId).toList(),
                        "paymentStatusPreserved", financeiroAnterior != null ? financeiroAnterior.name() : "UNKNOWN",
                        "productionStarted", false,
                        "tenantTemplate", resolveTenantTemplateCode(after),
                        "pedidoOrigem", resolvePedidoOrigem(after)
                ),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedidoId);
    }

    /**
     * Comando explícito de rejeição operacional de pedido ainda não aceite.
     *
     * O estado canônico atual não possui REJEITADO; nesta fase a rejeição é
     * persistida como CANCELADO com motivo, sem estorno ou confirmação de pagamento.
     */
    @Transactional
    public TenantPedidoDetalheResponse rejeitarPedido(Long pedidoId, String motivo, String ip, String userAgent) {
        TenantContext ctx = requireTenantCommandContext();
        Pedido pedido = loadPedido(ctx, pedidoId);
        validarTurnoObrigatorio(pedido);
        operationalTemplatePolicy.assertCanRejectPedido(pedido, resolveOrigem());

        if (pedido.getStatus() != StatusPedido.CRIADO) {
            logPedidoCommandBlocked(
                    pedido,
                    "REJECT_ORDER",
                    "Pedido só pode ser rejeitado antes do aceite.",
                    ip,
                    userAgent
            );
            throw new ConflictException("Pedido só pode ser rejeitado antes do aceite.");
        }

        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.NAO_PAGO) {
            logPedidoCommandBlocked(
                    pedido,
                    "REJECT_ORDER",
                    "Pedido com pagamento em curso ou confirmado não pode ser rejeitado nesta fase.",
                    ip,
                    userAgent
            );
            throw new ConflictException("Pedido com pagamento em curso ou confirmado não pode ser rejeitado nesta fase.");
        }

        return cancelarPedido(pedido, motivo, ip, userAgent, true);
    }

    private TenantPedidoDetalheResponse finalizarPedido(TenantContext ctx, Pedido pedido, String motivo, String ip, String userAgent) {
        // Para finalizar pedido, exige que todos os subpedidos estejam PRONTO (ou já ENTREGUE)
        List<SubPedido> subs = getSubPedidosOrThrow(pedido);
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

        return tenantAdminPedidoService.buscarDetalhe(pedido.getId());
    }

    private TenantPedidoDetalheResponse cancelarPedido(Pedido pedido, String motivo, String ip, String userAgent, boolean rejeicao) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(rejeicao ? "Motivo é obrigatório para rejeitar pedido." : "Motivo é obrigatório para cancelar pedido.");
        }

        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new ConflictException("Pedido pago não pode ser cancelado operacionalmente nesta fase.");
        }

        StatusPedido anterior = pedido.getStatus();

        // Cancela subpedidos se possível
        List<SubPedido> subs = getSubPedidosOrThrow(pedido);
        for (SubPedido sp : subs) {
            if (sp.getStatus() == null) continue;
            if (sp.getStatus().isTerminal()) continue;
            if (!sp.podeTransicionarPara(StatusSubPedido.CANCELADO)) {
                throw new ConflictException(rejeicao
                        ? "Não é possível rejeitar: existe subpedido em estado que não permite cancelamento."
                        : "Não é possível cancelar: existe subpedido em estado que não permite cancelamento.");
            }
        }

        for (SubPedido sp : subs) {
            if (sp.getStatus() == null) continue;
            if (sp.getStatus().isTerminal()) continue;
            StatusSubPedido statusAnterior = sp.getStatus();
            sp.setStatus(StatusSubPedido.CANCELADO);
            operationalEventLogService.logSubPedidoStatusChanged(
                    sp,
                    statusAnterior.name(),
                    StatusSubPedido.CANCELADO.name(),
                    resolveOrigem(),
                    motivo,
                    Map.of(
                            "command", rejeicao ? "REJECT_ORDER" : "CANCEL_ORDER",
                            "tenantTemplate", resolveTenantTemplateCode(pedido),
                            "pedidoOrigem", resolvePedidoOrigem(pedido)
                    ),
                    ip,
                    userAgent
            );
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
                Map.of(
                        "command", rejeicao ? "REJECT_ORDER" : "CANCEL_ORDER",
                        "cancelledSubPedidos", subs.stream().map(SubPedido::getId).toList(),
                        "paymentStatusPreserved", pedido.getStatusFinanceiro() != null ? pedido.getStatusFinanceiro().name() : "UNKNOWN",
                        "tenantTemplate", resolveTenantTemplateCode(pedido),
                        "pedidoOrigem", resolvePedidoOrigem(pedido)
                ),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedido.getId());
    }

    private TenantContext requireTenantCommandContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        return ctx;
    }

    private Pedido loadPedido(TenantContext ctx, Long pedidoId) {
        return pedidoRepository.findByIdAndTenantIdComItens(pedidoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
    }

    private List<SubPedido> getSubPedidosOrThrow(Pedido pedido) {
        List<SubPedido> subs = pedido.getSubPedidos() != null ? pedido.getSubPedidos() : List.of();
        if (subs.isEmpty()) {
            throw new ConflictException("Pedido não possui subpedidos para transição operacional.");
        }
        return subs;
    }

    private void validarTurnoObrigatorio(Pedido pedido) {
        if (!operacaoProperties.isTurnoObrigatorio()) {
            return;
        }
        if (!operationalTemplatePolicy.requiresTurno(pedido)) {
            return;
        }
        if (pedido.getTurnoOperacional() == null ||
                !(pedido.getTurnoOperacional().getStatus() == TurnoOperacionalStatus.ABERTO ||
                        pedido.getTurnoOperacional().getStatus() == TurnoOperacionalStatus.EM_FECHO)) {
            throw new TurnoObrigatorioException("Abra um turno para executar ações operacionais sobre pedidos.");
        }
    }

    private void logPedidoCommandBlocked(Pedido pedido, String command, String message, String ip, String userAgent) {
        operationalEventLogService.logTransitionBlocked(
                OperationalEntityType.PEDIDO,
                pedido.getId(),
                resolveOrigem(),
                message,
                Map.of(
                        "command", command,
                        "statusOperacional", pedido.getStatus() != null ? pedido.getStatus().name() : "UNKNOWN",
                        "statusFinanceiro", pedido.getStatusFinanceiro() != null ? pedido.getStatusFinanceiro().name() : "UNKNOWN",
                        "tenantTemplate", resolveTenantTemplateCode(pedido),
                        "pedidoOrigem", resolvePedidoOrigem(pedido)
                ),
                ip,
                userAgent
        );
    }

    private String resolveTenantTemplateCode(Pedido pedido) {
        return operationalTemplatePolicy.resolveTemplateCode(pedido);
    }

    private String resolvePedidoOrigem(Pedido pedido) {
        return operationalTemplatePolicy.resolvePedidoOrigem(pedido, null).name();
    }

    private OperationalOrigem resolveOrigem() {
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_CASHIER)) return OperationalOrigem.TENANT_CASHIER;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OPERATOR)) return OperationalOrigem.TENANT_OPERATOR;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_ADMIN)) return OperationalOrigem.TENANT_ADMIN;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER)) return OperationalOrigem.TENANT_OWNER;
        return OperationalOrigem.SYSTEM;
    }
}
