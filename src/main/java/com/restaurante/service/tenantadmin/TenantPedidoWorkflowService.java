package com.restaurante.service.tenantadmin;

import com.restaurante.dto.request.ConfirmarPagamentoManualPedidoRequest;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentUsageContext;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.EventLogService;
import com.restaurante.service.SubPedidoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.service.operacional.PublicOrderStateMachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantPedidoWorkflowService {

    private final TenantGuard tenantGuard;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoService subPedidoService;
    private final TenantAdminPedidoService tenantAdminPedidoService;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OrdemPagamentoService ordemPagamentoService;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final UserRepository userRepository;
    private final EventLogService eventLogService;
    private final OperationalEventLogService operationalEventLogService;
    private final PublicOrderStateMachineService publicOrderStateMachineService;

    @Transactional
    public TenantPedidoDetalheResponse aceitarPedido(Long pedidoId, String observacao, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );

        Pedido pedido = buscarPedido(ctx.tenantId(), pedidoId);
        publicOrderStateMachineService.assertPedidoPodeSerAceite(pedido);

        StatusPedido statusAnterior = pedido.getStatus();
        List<Long> acceptedSubPedidos = pedido.getSubPedidos().stream()
                .filter(subPedido -> subPedido.getStatus() == StatusSubPedido.CRIADO)
                .map(SubPedido::getId)
                .toList();
        for (Long subPedidoId : acceptedSubPedidos) {
            subPedidoService.confirmar(subPedidoId);
        }

        Pedido atualizado = buscarPedido(ctx.tenantId(), pedidoId);
        if (acceptedSubPedidos.isEmpty()) {
            if (atualizado.getStatus() == StatusPedido.EM_ANDAMENTO) {
                return tenantAdminPedidoService.buscarDetalhe(pedidoId);
            }
            throw new ConflictException("Pedido não possui subpedidos pendentes de aceite.");
        }

        String detalhe = buildAcceptMessage(observacao);
        eventLogService.registrarEventoPedido(atualizado, statusAnterior, atualizado.getStatus(), resolveActor(ctx), detalhe);
        operationalEventLogService.logPedidoStatusChanged(
                atualizado,
                statusAnterior != null ? statusAnterior.name() : null,
                atualizado.getStatus() != null ? atualizado.getStatus().name() : null,
                resolveOrigem(),
                detalhe,
                Map.of("acceptedSubPedidos", acceptedSubPedidos),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedidoId);
    }

    @Transactional
    public TenantPedidoDetalheResponse rejeitarPedido(Long pedidoId, String motivo, String ip, String userAgent) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo é obrigatório");
        }

        TenantContext ctx = requireTenantContext();
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );

        Pedido pedido = buscarPedido(ctx.tenantId(), pedidoId);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return tenantAdminPedidoService.buscarDetalhe(pedidoId);
        }
        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
            throw new ConflictException("Pedido finalizado não pode ser rejeitado.");
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new ConflictException("Pedido pago não pode ser rejeitado após confirmação de pagamento.");
        }

        StatusPedido statusAnterior = pedido.getStatus();
        List<Long> cancelledSubPedidos = new ArrayList<>();
        for (SubPedido subPedido : pedido.getSubPedidos()) {
            if (subPedido.getStatus() == null || subPedido.getStatus() == StatusSubPedido.CANCELADO) {
                continue;
            }
            if (subPedido.getStatus().isTerminal()) {
                throw new ConflictException("Pedido não pode ser rejeitado porque possui subpedido já entregue.");
            }
            cancelledSubPedidos.add(subPedido.getId());
        }

        for (Long subPedidoId : cancelledSubPedidos) {
            subPedidoService.cancelarInterno(subPedidoId, motivo);
        }

        OrdemPagamento ordem = ordemPagamentoRepository.findFirstByPedidoIdOrderByCreatedAtDesc(pedidoId).orElse(null);
        boolean ordemCancelada = false;
        if (ordem != null && ordem.getStatus() == OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO) {
            ordem.setStatus(OrdemPagamentoStatus.CANCELADA);
            ordemPagamentoRepository.save(ordem);
            ordemCancelada = true;
        }

        Pedido atualizado = buscarPedido(ctx.tenantId(), pedidoId);
        if (atualizado.getStatus() != StatusPedido.CANCELADO) {
            atualizado.setStatus(StatusPedido.CANCELADO);
            pedidoRepository.save(atualizado);
        }

        eventLogService.registrarEventoPedido(atualizado, statusAnterior, atualizado.getStatus(), resolveActor(ctx), motivo.trim());
        operationalEventLogService.logPedidoStatusChanged(
                atualizado,
                statusAnterior != null ? statusAnterior.name() : null,
                atualizado.getStatus().name(),
                resolveOrigem(),
                motivo.trim(),
                Map.of(
                        "cancelledSubPedidos", cancelledSubPedidos,
                        "ordemPagamentoCancelada", ordemCancelada
                ),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedidoId);
    }

    @Transactional
    public TenantPedidoDetalheResponse confirmarPagamentoManual(Long pedidoId,
                                                                ConfirmarPagamentoManualPedidoRequest request,
                                                                String ip,
                                                                String userAgent) {
        TenantContext ctx = requireTenantContext();
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );

        if (request.getMetodoPagamento() == null || request.getMetodoPagamento() == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("Confirmação manual só é permitida para métodos CASH ou TPA.");
        }

        Pedido pedido = buscarPedido(ctx.tenantId(), pedidoId);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ConflictException("Pedido cancelado não pode receber confirmação de pagamento.");
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.ESTORNADO) {
            throw new ConflictException("Pedido estornado não pode receber confirmação de pagamento.");
        }
        OrdemPagamento ordem = ordemPagamentoRepository.findFirstByPedidoIdOrderByCreatedAtDesc(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (!ordem.getTenant().getId().equals(ctx.tenantId())) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        if (ordem.getStatus() == OrdemPagamentoStatus.CONFIRMADA) {
            return tenantAdminPedidoService.buscarDetalhe(pedidoId);
        }
        if (ordem.getStatus() != OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO) {
            throw new ConflictException("Ordem de pagamento não pode ser confirmada no estado atual.");
        }
        if (ordem.getMetodoSolicitado() != null && ordem.getMetodoSolicitado() != request.getMetodoPagamento()) {
            throw new BusinessException("Método informado não corresponde ao método solicitado para a ordem.");
        }

        PaymentMethodCode code = request.getMetodoPagamento() == MetodoPagamentoManual.CASH
                ? PaymentMethodCode.CASH
                : PaymentMethodCode.TPA;
        var tenantMethod = tenantPaymentMethodService.validateMethodAllowed(
                ctx.tenantId(),
                code,
                PaymentUsageContext.TENANT_ADMIN,
                PaymentDestination.PEDIDO,
                ordem.getValor()
        );
        if (!tenantMethod.isRequiresManualConfirmation()) {
            throw new BusinessException("Método informado não permite confirmação manual.");
        }

        if (ordem.getConfirmadoPorUser() == null && ctx.userId() != null) {
            User user = userRepository.findById(ctx.userId()).orElse(null);
            ordem.setConfirmadoPorUser(user);
        }

        Pagamento pagamento = ordemPagamentoService.aplicarConfirmacaoManualOrdem(
                ordem,
                request.getMetodoPagamento(),
                request.getValor(),
                request.getReferenciaComprovativo(),
                request.getObservacao()
        );

        String detalhe = "Pagamento manual confirmado via " + request.getMetodoPagamento().name();
        eventLogService.registrarEventoPedido(pedido, pedido.getStatus(), pedido.getStatus(), resolveActor(ctx), detalhe);
        operationalEventLogService.logOrdemPagamentoEvent(
                OperationalEventType.ORDEM_PAGAMENTO_CONFIRMADA_MANUAL,
                ordem,
                resolveOrigem(),
                detalhe,
                Map.of(
                        "pedidoId", pedido.getId(),
                        "pagamentoId", pagamento != null ? pagamento.getId() : null,
                        "metodo", request.getMetodoPagamento().name(),
                        "valor", ordem.getValor()
                ),
                ip,
                userAgent
        );

        return tenantAdminPedidoService.buscarDetalhe(pedidoId);
    }

    private Pedido buscarPedido(Long tenantId, Long pedidoId) {
        return pedidoRepository.findByIdAndTenantIdComSubPedidos(pedidoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
    }

    private TenantContext requireTenantContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(ctx.tenantId());
        tenantGuard.assertTenantActive(ctx.tenantId());
        return ctx;
    }

    private String resolveActor(TenantContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return "tenant";
        }
        return userRepository.findById(ctx.userId())
                .map(User::getUsername)
                .orElse("user#" + ctx.userId());
    }

    private OperationalOrigem resolveOrigem() {
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_FINANCE)) return OperationalOrigem.TENANT_FINANCE;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_CASHIER)) return OperationalOrigem.TENANT_CASHIER;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OPERATOR)) return OperationalOrigem.TENANT_OPERATOR;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_ADMIN)) return OperationalOrigem.TENANT_ADMIN;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER)) return OperationalOrigem.TENANT_OWNER;
        return OperationalOrigem.SYSTEM;
    }

    private String buildAcceptMessage(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return "Pedido aceite pelo operador";
        }
        return "Pedido aceite pelo operador: " + observacao.trim();
    }
}
