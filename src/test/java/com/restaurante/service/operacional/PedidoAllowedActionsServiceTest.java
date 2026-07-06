package com.restaurante.service.operacional;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.PedidoAllowedAction;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantResolutionSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoAllowedActionsServiceTest {

    private final OperacaoProperties operacaoProperties = new OperacaoProperties();
    private final PedidoAllowedActionsService service = new PedidoAllowedActionsService(
            new OperationalTemplatePolicy(),
            operacaoProperties
    );

    @Test
    void pedidoCriadoPublicoQrRetornaAcceptERejectParaOperadorPermitido() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.CRIADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_OPERATOR)
        );

        assertThat(capabilities.allowedActions())
                .contains(PedidoAllowedAction.ACCEPT_ORDER, PedidoAllowedAction.REJECT_ORDER, PedidoAllowedAction.VIEW_PAYMENT)
                .doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
    }

    @Test
    void pedidoAceiteNaoRetornaAcceptNovamente() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_OPERATOR)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.ACCEPT_ORDER);
        assertThat(capabilities.actionReasons()).containsEntry(PedidoAllowedAction.ACCEPT_ORDER, "Pedido já saiu do estado inicial.");
    }

    @Test
    void pedidoPagoNaoRetornaRejectOrder() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.PAGO, true, StatusSubPedido.CRIADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_OPERATOR)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.REJECT_ORDER);
        assertThat(capabilities.actionReasons()).containsEntry(
                PedidoAllowedAction.REJECT_ORDER,
                "Pedido com pagamento em curso ou confirmado não pode ser rejeitado nesta fase."
        );
    }

    @Test
    void pedidoCriadoAntesDoAceiteNaoRetornaConfirmPayment() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.CRIADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
        assertThat(capabilities.actionReasons()).containsEntry(
                PedidoAllowedAction.CONFIRM_PAYMENT,
                "Pagamento disponível apenas após aceite do pedido."
        );
    }

    @Test
    void pedidoAceiteRetornaViewPaymentMasNaoConfirmPaymentSemContratoReal() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions()).contains(PedidoAllowedAction.VIEW_PAYMENT);
        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
        assertThat(capabilities.actionReasons()).containsEntry(
                PedidoAllowedAction.CONFIRM_PAYMENT,
                "Confirmação de pagamento deve ocorrer em fluxo financeiro real."
        );
    }

    @Test
    void kdsNaoRecebeAcaoDePagamento() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_KITCHEN)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
        assertThat(capabilities.actionReasons()).containsEntry(PedidoAllowedAction.CONFIRM_PAYMENT, "Ator não autorizado para esta ação.");
    }

    @Test
    void caixaNaoRecebeAcaoDeProducao() {
        Pedido pedido = pedido("CAIXA", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions())
                .doesNotContain(PedidoAllowedAction.START_PREPARATION, PedidoAllowedAction.MARK_READY, PedidoAllowedAction.MARK_DELIVERED);
    }

    @Test
    void roleSemPermissaoNaoRecebeAcceptOrder() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.CRIADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_FINANCE)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.ACCEPT_ORDER);
        assertThat(capabilities.actionReasons()).containsEntry(PedidoAllowedAction.ACCEPT_ORDER, "Ator não autorizado para esta ação.");
    }

    @Test
    void turnoObrigatorioBloqueiaAllowedActionsOperacionais() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, false, StatusSubPedido.CRIADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_OPERATOR)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.ACCEPT_ORDER, PedidoAllowedAction.REJECT_ORDER);
        assertThat(capabilities.actionReasons()).containsEntry(
                PedidoAllowedAction.ACCEPT_ORDER,
                "Abra um turno para executar ações operacionais sobre pedidos."
        );
    }

    @Test
    void subPedidoTerminalNaoLiberaRejectOuCancel() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.CANCELADO);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_OPERATOR)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.REJECT_ORDER, PedidoAllowedAction.CANCEL_ORDER);
        assertThat(capabilities.actionReasons()).containsEntry(PedidoAllowedAction.REJECT_ORDER, "Pedido não possui subpedidos elegíveis para esta ação.");
    }

    private Pedido pedido(
            String templateCode,
            StatusPedido status,
            StatusFinanceiroPedido statusFinanceiro,
            boolean turnoAberto,
            StatusSubPedido subPedidoStatus
    ) {
        Tenant tenant = new Tenant();
        tenant.setTemplateCode(templateCode);
        tenant.setTemplateVersion(1);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setTenant(tenant);
        sessao.setQrCodeSessao("qr-principal");
        Mesa mesa = new Mesa();
        mesa.setTenant(tenant);
        mesa.setReferencia("Mesa 1");
        sessao.setMesa(mesa);

        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setSessaoConsumo(sessao);
        pedido.setStatus(status);
        pedido.setStatusFinanceiro(statusFinanceiro);

        TurnoOperacional turno = new TurnoOperacional();
        turno.setStatus(turnoAberto ? TurnoOperacionalStatus.ABERTO : TurnoOperacionalStatus.FECHADO);
        pedido.setTurnoOperacional(turno);

        SubPedido subPedido = new SubPedido();
        subPedido.setPedido(pedido);
        subPedido.setStatus(subPedidoStatus);
        pedido.setSubPedidos(List.of(subPedido));
        return pedido;
    }

    private TenantContext ctx(TenantUserRole role) {
        return new TenantContext(1L, "tenant-demo", 10L, Set.of(role.name()), TenantResolutionSource.JWT, false, false);
    }
}
