package com.restaurante.service.operacional;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.PedidoAllowedAction;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantResolutionSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PedidoAllowedActionsServiceTest {

    private final OperacaoProperties operacaoProperties = new OperacaoProperties();
    private final OrdemPagamentoRepository ordemPagamentoRepository = Mockito.mock(OrdemPagamentoRepository.class);
    private final OperationalCapabilitiesPolicy operationalCapabilitiesPolicy = Mockito.mock(OperationalCapabilitiesPolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final PedidoAllowedActionsService service;

    PedidoAllowedActionsServiceTest() {
        when(operationalCapabilitiesPolicy.canUseProduction(Mockito.any(Pedido.class)))
                .thenAnswer(invocation -> !isPonto(invocation.getArgument(0)));
        when(operationalCapabilitiesPolicy.canDeliverWithoutReady(Mockito.any(Pedido.class)))
                .thenAnswer(invocation -> isPonto(invocation.getArgument(0)));
        service = new PedidoAllowedActionsService(
                new OperationalTemplatePolicy(),
                operacaoProperties,
                ordemPagamentoRepository,
                clock,
                operationalCapabilitiesPolicy
        );
    }

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
    void pedidoAceiteRetornaViewPaymentMasNaoConfirmPaymentSemOrdem() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions()).contains(PedidoAllowedAction.VIEW_PAYMENT);
        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
        assertThat(capabilities.actionReasons()).containsEntry(
                PedidoAllowedAction.CONFIRM_PAYMENT,
                "Ordem de pagamento indisponível."
        );
    }

    @Test
    void pedidoAceiteComOrdemValidaRetornaConfirmPaymentParaCaixa() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);
        OrdemPagamento ordem = ordem(pedido, OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO, now().plusMinutes(10));
        when(ordemPagamentoRepository.findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(1L, 100L))
                .thenReturn(Optional.of(ordem));

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions()).contains(PedidoAllowedAction.CONFIRM_PAYMENT);
    }

    @Test
    void pedidoAceiteComOrdemExpiradaNaoRetornaConfirmPayment() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PENDENTE);
        OrdemPagamento ordem = ordem(pedido, OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO, now().minusMinutes(1));
        when(ordemPagamentoRepository.findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(1L, 100L))
                .thenReturn(Optional.of(ordem));

        PedidoAllowedActionsService.PedidoCapabilities capabilities = service.evaluate(
                pedido,
                ctx(TenantUserRole.TENANT_CASHIER)
        );

        assertThat(capabilities.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
        assertThat(capabilities.actionReasons()).containsEntry(PedidoAllowedAction.CONFIRM_PAYMENT, "Ordem de pagamento expirada.");
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
        tenant.setId(1L);
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
        pedido.setId(100L);
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

    private boolean isPonto(Pedido pedido) {
        return pedido != null
                && pedido.getTenant() != null
                && pedido.getTenant().getTemplateCode() != null
                && pedido.getTenant().getTemplateCode().startsWith("CONSUMA_PONTO");
    }

    private OrdemPagamento ordem(Pedido pedido, OrdemPagamentoStatus status, LocalDateTime expiresAt) {
        OrdemPagamento ordem = new OrdemPagamento();
        ordem.setId(200L);
        ordem.setTenant(pedido.getTenant());
        ordem.setPedido(pedido);
        ordem.setStatus(status);
        ordem.setValor(new BigDecimal("10.00"));
        ordem.setMoeda("AOA");
        ordem.setMetodoSolicitado(MetodoPagamentoManual.TPA);
        ordem.setExpiresAt(expiresAt);
        return ordem;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private TenantContext ctx(TenantUserRole role) {
        return new TenantContext(1L, "tenant-demo", 10L, Set.of(role.name()), TenantResolutionSource.JWT, false, false);
    }
}
