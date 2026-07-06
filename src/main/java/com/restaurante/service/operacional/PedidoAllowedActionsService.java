package com.restaurante.service.operacional;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PedidoAllowedAction;
import com.restaurante.model.enums.PedidoOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoAllowedActionsService {

    private static final String REASON_TURNO = "Abra um turno para executar ações operacionais sobre pedidos.";
    private static final String REASON_ACTOR = "Ator não autorizado para esta ação.";
    private static final String REASON_STATUS_INITIAL = "Pedido já saiu do estado inicial.";
    private static final String REASON_STATUS_TERMINAL = "Pedido está em estado terminal.";
    private static final String REASON_PAYMENT_REJECT = "Pedido com pagamento em curso ou confirmado não pode ser rejeitado nesta fase.";
    private static final String REASON_PAYMENT_CANCEL = "Pedido pago não pode ser cancelado operacionalmente nesta fase.";
    private static final String REASON_SUBPEDIDOS = "Pedido não possui subpedidos elegíveis para esta ação.";
    private static final String REASON_PAYMENT_ORDER_MISSING = "Ordem de pagamento indisponível.";
    private static final String REASON_PAYMENT_ORDER_EXPIRED = "Ordem de pagamento expirada.";
    private static final String REASON_PAYMENT_ORDER_STATUS = "Ordem de pagamento não está aguardando confirmação.";
    private static final String REASON_PAYMENT_NOT_CONFIRMED = "Entrega permitida apenas após pagamento confirmado.";

    private final OperationalTemplatePolicy operationalTemplatePolicy;
    private final OperacaoProperties operacaoProperties;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final Clock clock;

    public PedidoCapabilities evaluate(Pedido pedido, TenantContext ctx) {
        if (pedido == null) {
            return new PedidoCapabilities(Set.of(), Map.of());
        }

        OperationalOrigem actor = resolveActor(ctx);
        String template = operationalTemplatePolicy.resolveTemplateCode(pedido);
        PedidoOrigem origem = operationalTemplatePolicy.resolvePedidoOrigem(pedido, actor);

        Set<PedidoAllowedAction> allowed = new LinkedHashSet<>();
        Map<PedidoAllowedAction, String> reasons = new EnumMap<>(PedidoAllowedAction.class);

        maybeAddViewPayment(actor, allowed);
        maybeAddAccept(pedido, actor, template, origem, allowed, reasons);
        maybeAddReject(pedido, actor, template, origem, allowed, reasons);
        maybeAddCancel(pedido, actor, template, origem, allowed, reasons);
        maybeAddPayment(pedido, actor, template, origem, allowed, reasons);
        maybeAddProduction(pedido, actor, template, origem, allowed, reasons);

        return new PedidoCapabilities(allowed, reasons);
    }

    private void maybeAddViewPayment(OperationalOrigem actor, Set<PedidoAllowedAction> allowed) {
        if (actor == OperationalOrigem.TENANT_OWNER
                || actor == OperationalOrigem.TENANT_ADMIN
                || actor == OperationalOrigem.TENANT_OPERATOR
                || actor == OperationalOrigem.TENANT_CASHIER
                || actor == OperationalOrigem.TENANT_FINANCE) {
            allowed.add(PedidoAllowedAction.VIEW_PAYMENT);
        }
    }

    private void maybeAddAccept(
            Pedido pedido,
            OperationalOrigem actor,
            String template,
            PedidoOrigem origem,
            Set<PedidoAllowedAction> allowed,
            Map<PedidoAllowedAction, String> reasons
    ) {
        PedidoAllowedAction action = PedidoAllowedAction.ACCEPT_ORDER;
        if (!operationalTemplatePolicy.canAccept(actor, template, origem)) {
            reasons.put(action, REASON_ACTOR);
            return;
        }
        if (!hasRequiredTurno(pedido)) {
            reasons.put(action, REASON_TURNO);
            return;
        }
        if (pedido.getStatus() != StatusPedido.CRIADO) {
            reasons.put(action, REASON_STATUS_INITIAL);
            return;
        }
        if (!allSubPedidosAre(pedido, StatusSubPedido.CRIADO)) {
            reasons.put(action, REASON_SUBPEDIDOS);
            return;
        }
        allowed.add(action);
    }

    private void maybeAddReject(
            Pedido pedido,
            OperationalOrigem actor,
            String template,
            PedidoOrigem origem,
            Set<PedidoAllowedAction> allowed,
            Map<PedidoAllowedAction, String> reasons
    ) {
        PedidoAllowedAction action = PedidoAllowedAction.REJECT_ORDER;
        if (!operationalTemplatePolicy.canReject(actor, template, origem)) {
            reasons.put(action, REASON_ACTOR);
            return;
        }
        if (!hasRequiredTurno(pedido)) {
            reasons.put(action, REASON_TURNO);
            return;
        }
        if (pedido.getStatus() != StatusPedido.CRIADO) {
            reasons.put(action, REASON_STATUS_INITIAL);
            return;
        }
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.NAO_PAGO) {
            reasons.put(action, REASON_PAYMENT_REJECT);
            return;
        }
        if (!hasCancelableSubPedidos(pedido)) {
            reasons.put(action, REASON_SUBPEDIDOS);
            return;
        }
        allowed.add(action);
    }

    private void maybeAddCancel(
            Pedido pedido,
            OperationalOrigem actor,
            String template,
            PedidoOrigem origem,
            Set<PedidoAllowedAction> allowed,
            Map<PedidoAllowedAction, String> reasons
    ) {
        PedidoAllowedAction action = PedidoAllowedAction.CANCEL_ORDER;
        if (!operationalTemplatePolicy.canReject(actor, template, origem)) {
            reasons.put(action, REASON_ACTOR);
            return;
        }
        if (!hasRequiredTurno(pedido)) {
            reasons.put(action, REASON_TURNO);
            return;
        }
        if (pedido.getStatus() == null || pedido.getStatus().isTerminal()) {
            reasons.put(action, REASON_STATUS_TERMINAL);
            return;
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            reasons.put(action, REASON_PAYMENT_CANCEL);
            return;
        }
        if (!hasCancelableSubPedidos(pedido)) {
            reasons.put(action, REASON_SUBPEDIDOS);
            return;
        }
        allowed.add(action);
    }

    private void maybeAddPayment(
            Pedido pedido,
            OperationalOrigem actor,
            String template,
            PedidoOrigem origem,
            Set<PedidoAllowedAction> allowed,
            Map<PedidoAllowedAction, String> reasons
    ) {
        PedidoAllowedAction action = PedidoAllowedAction.CONFIRM_PAYMENT;
        if (!operationalTemplatePolicy.canConfirmPayment(actor, template, origem)) {
            reasons.put(action, REASON_ACTOR);
            return;
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO
                || pedido.getStatusFinanceiro() == StatusFinanceiroPedido.ESTORNADO) {
            reasons.put(action, "Pedido não possui pagamento pendente.");
            return;
        }
        if (operationalTemplatePolicy.requiresAcceptanceBeforePayment(pedido, actor)
                && pedido.getStatus() == StatusPedido.CRIADO) {
            reasons.put(action, "Pagamento disponível apenas após aceite do pedido.");
            return;
        }
        OrdemPagamento ordem = latestPaymentOrder(pedido);
        if (ordem == null) {
            reasons.put(action, REASON_PAYMENT_ORDER_MISSING);
            return;
        }
        if (ordem.getStatus() != OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO) {
            reasons.put(action, REASON_PAYMENT_ORDER_STATUS);
            return;
        }
        if (ordem.isExpirada(LocalDateTime.now(clock))) {
            reasons.put(action, REASON_PAYMENT_ORDER_EXPIRED);
            return;
        }
        allowed.add(action);
    }

    private void maybeAddProduction(
            Pedido pedido,
            OperationalOrigem actor,
            String template,
            PedidoOrigem origem,
            Set<PedidoAllowedAction> allowed,
            Map<PedidoAllowedAction, String> reasons
    ) {
        boolean canMoveProduction = operationalTemplatePolicy.canMoveProduction(actor, template, origem);
        if (!canMoveProduction) {
            reasons.put(PedidoAllowedAction.START_PREPARATION, REASON_ACTOR);
            reasons.put(PedidoAllowedAction.MARK_READY, REASON_ACTOR);
            reasons.put(PedidoAllowedAction.MARK_DELIVERED, REASON_ACTOR);
            return;
        }
        if (!hasRequiredTurno(pedido)) {
            reasons.put(PedidoAllowedAction.START_PREPARATION, REASON_TURNO);
            reasons.put(PedidoAllowedAction.MARK_READY, REASON_TURNO);
            reasons.put(PedidoAllowedAction.MARK_DELIVERED, REASON_TURNO);
            return;
        }

        List<SubPedido> subPedidos = subPedidos(pedido);
        if (subPedidos.stream().anyMatch(sp -> sp.getStatus() == StatusSubPedido.PENDENTE)) {
            allowed.add(PedidoAllowedAction.START_PREPARATION);
        } else {
            reasons.put(PedidoAllowedAction.START_PREPARATION, REASON_SUBPEDIDOS);
        }
        if (subPedidos.stream().anyMatch(sp -> sp.getStatus() == StatusSubPedido.EM_PREPARACAO)) {
            allowed.add(PedidoAllowedAction.MARK_READY);
        } else {
            reasons.put(PedidoAllowedAction.MARK_READY, REASON_SUBPEDIDOS);
        }
        boolean isOptionalKitchen = operationalTemplatePolicy.productionFlow(template, origem) == OperationalTemplatePolicy.ProductionFlow.OPTIONAL;
        if (!subPedidos.isEmpty() && subPedidos.stream().allMatch(sp ->
                sp.getStatus() == StatusSubPedido.PRONTO || sp.getStatus() == StatusSubPedido.ENTREGUE ||
                (isOptionalKitchen && sp.getStatus() == StatusSubPedido.PENDENTE))) {
            // MARK_DELIVERED exige pagamento confirmado por contrato operacional.
            if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
                reasons.put(PedidoAllowedAction.MARK_DELIVERED, REASON_PAYMENT_NOT_CONFIRMED);
            } else if (pedido.getStatus() == StatusPedido.FINALIZADO) {
                reasons.put(PedidoAllowedAction.MARK_DELIVERED, REASON_STATUS_TERMINAL);
            } else {
                allowed.add(PedidoAllowedAction.MARK_DELIVERED);
            }
        } else {
            reasons.put(PedidoAllowedAction.MARK_DELIVERED, REASON_SUBPEDIDOS);
        }
    }

    private boolean hasRequiredTurno(Pedido pedido) {
        if (!operacaoProperties.isTurnoObrigatorio()) {
            return true;
        }
        if (!operationalTemplatePolicy.requiresTurno(pedido)) {
            return true;
        }
        return pedido.getTurnoOperacional() != null
                && (pedido.getTurnoOperacional().getStatus() == TurnoOperacionalStatus.ABERTO
                || pedido.getTurnoOperacional().getStatus() == TurnoOperacionalStatus.EM_FECHO);
    }

    private boolean allSubPedidosAre(Pedido pedido, StatusSubPedido status) {
        List<SubPedido> subPedidos = subPedidos(pedido);
        return !subPedidos.isEmpty() && subPedidos.stream().allMatch(sp -> sp.getStatus() == status);
    }

    private boolean hasCancelableSubPedidos(Pedido pedido) {
        List<SubPedido> subPedidos = subPedidos(pedido);
        return !subPedidos.isEmpty() && subPedidos.stream()
                .allMatch(sp -> sp.getStatus() != null
                        && !sp.getStatus().isTerminal()
                        && sp.getStatus().podeTransicionarPara(StatusSubPedido.CANCELADO));
    }

    private List<SubPedido> subPedidos(Pedido pedido) {
        return pedido.getSubPedidos() != null ? pedido.getSubPedidos() : List.of();
    }

    private OrdemPagamento latestPaymentOrder(Pedido pedido) {
        if (pedido == null || pedido.getId() == null || pedido.getTenant() == null || pedido.getTenant().getId() == null) {
            return null;
        }
        return ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(pedido.getTenant().getId(), pedido.getId())
                .orElse(null);
    }

    private OperationalOrigem resolveActor(TenantContext ctx) {
        if (ctx == null) {
            return OperationalOrigem.SYSTEM;
        }
        if (ctx.platformAdmin()) {
            return OperationalOrigem.TENANT_ADMIN;
        }
        Set<String> roles = ctx.roles() != null ? ctx.roles() : Collections.emptySet();
        if (roles.contains(TenantUserRole.TENANT_OWNER.name())) return OperationalOrigem.TENANT_OWNER;
        if (roles.contains(TenantUserRole.TENANT_ADMIN.name())) return OperationalOrigem.TENANT_ADMIN;
        if (roles.contains(TenantUserRole.TENANT_OPERATOR.name())) return OperationalOrigem.TENANT_OPERATOR;
        if (roles.contains(TenantUserRole.TENANT_CASHIER.name())) return OperationalOrigem.TENANT_CASHIER;
        if (roles.contains(TenantUserRole.TENANT_FINANCE.name())) return OperationalOrigem.TENANT_FINANCE;
        if (roles.contains(TenantUserRole.TENANT_KITCHEN.name())) return OperationalOrigem.TENANT_KITCHEN;
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_GERENTE")) return OperationalOrigem.TENANT_ADMIN;
        if (roles.contains("ROLE_ATENDENTE")) return OperationalOrigem.TENANT_OPERATOR;
        if (roles.contains("ROLE_COZINHA")) return OperationalOrigem.TENANT_KITCHEN;
        return OperationalOrigem.SYSTEM;
    }

    public record PedidoCapabilities(
            Set<PedidoAllowedAction> allowedActions,
            Map<PedidoAllowedAction, String> actionReasons
    ) {
    }
}
