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

/**
 * Testes de contrato para MARK_DELIVERED — Passo 7 do PROMPT-BACKEND-CONSUMA-DEMO-FREEZY-DELIVERY-CONTRACT-001.
 *
 * Cobre:
 * 1. Pedido sem pagamento PAGO não retorna MARK_DELIVERED.
 * 2. Pedido pago com subpedidos PRONTO retorna MARK_DELIVERED para operador autorizado.
 * 3. Pedido cancelado não retorna MARK_DELIVERED.
 * 4. Pedido já finalizado não retorna MARK_DELIVERED.
 * 5. Actor sem permissão não recebe MARK_DELIVERED.
 * 6. Turno obrigatório fechado bloqueia MARK_DELIVERED.
 * 7. Sem subpedidos elegíveis não retorna MARK_DELIVERED.
 * 8. MARK_DELIVERED desaparece de allowedActions após pedido FINALIZADO.
 * 9. Cliente público (SYSTEM) não recebe MARK_DELIVERED.
 */
class PedidoEntregaAllowedActionsTest {

    private final OperacaoProperties operacaoProperties = new OperacaoProperties();
    private final OrdemPagamentoRepository ordemPagamentoRepository = Mockito.mock(OrdemPagamentoRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final PedidoAllowedActionsService service = new PedidoAllowedActionsService(
            new OperationalTemplatePolicy(),
            operacaoProperties,
            ordemPagamentoRepository,
            clock
    );

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 1: Pedido sem pagamento PAGO não retorna MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void pedidoSemPagamentoPagoNaoRetornaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsEntry(
                PedidoAllowedAction.MARK_DELIVERED,
                "Entrega permitida apenas após pagamento confirmado."
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 2: Pedido pago com subpedidos PRONTO retorna MARK_DELIVERED para operador
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void pedidoPagoComSubPedidosProntoRetornaMarkDeliveredParaOperador() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).contains(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).doesNotContainKey(PedidoAllowedAction.MARK_DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 3: Pedido cancelado não retorna MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void pedidoCanceladoNaoRetornaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.CANCELADO,
                StatusFinanceiroPedido.NAO_PAGO, true, StatusSubPedido.CANCELADO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 4: Pedido já finalizado não retorna MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void pedidoJaFinalizadoNaoRetornaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.FINALIZADO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.ENTREGUE);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsKey(PedidoAllowedAction.MARK_DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 5: Actor sem permissão (KITCHEN) não recebe MARK_DELIVERED para template CAIXA
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void actorSemPermissaoNaoRecebeMarkDelivered() {
        Pedido pedido = pedido("CAIXA", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_CASHIER));

        // Template CAIXA não permite kitchen flow
        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsKey(PedidoAllowedAction.MARK_DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 6: Turno obrigatório fechado bloqueia MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void turnoFechadoBloqueiaMarkDelivered() {
        operacaoProperties.setTurnoObrigatorio(true);
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, false /* turno fechado */, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsEntry(
                PedidoAllowedAction.MARK_DELIVERED,
                "Abra um turno para executar ações operacionais sobre pedidos."
        );
        // reset
        operacaoProperties.setTurnoObrigatorio(false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 7: Subpedidos ainda em produção não libera MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void subPedidosEmProducaoNaoLiberaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.EM_PREPARACAO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OPERATOR));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsEntry(
                PedidoAllowedAction.MARK_DELIVERED,
                "Pedido não possui subpedidos elegíveis para esta ação."
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 8: CONFIRM_PAYMENT não reaparece após pedido PAGO
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void confirmPaymentNaoAparecePedidoJaPago() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_CASHIER));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.CONFIRM_PAYMENT);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 9: Contexto nulo (cliente público) não recebe MARK_DELIVERED
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void contextoNuloNaoRecebeMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, null);

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TESTE 10: MARK_DELIVERED aparece para OWNER com pedido pago e subpedidos prontos
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    void ownerComPedidoPagoRecebeMARKDelivered() {
        Pedido pedido = pedido("CONSUMA_PONTO", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PRONTO);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OWNER));

        assertThat(caps.allowedActions()).contains(PedidoAllowedAction.MARK_DELIVERED);
    }

    @Test
    void pontoPagoComSubPedidoPendenteLiberaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_PONTO", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OWNER));

        assertThat(caps.allowedActions()).contains(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).doesNotContainKey(PedidoAllowedAction.MARK_DELIVERED);
    }

    @Test
    void restPagoComSubPedidoPendenteNaoLiberaMarkDelivered() {
        Pedido pedido = pedido("CONSUMA_REST", StatusPedido.EM_ANDAMENTO,
                StatusFinanceiroPedido.PAGO, true, StatusSubPedido.PENDENTE);

        PedidoAllowedActionsService.PedidoCapabilities caps = service.evaluate(pedido, ctx(TenantUserRole.TENANT_OWNER));

        assertThat(caps.allowedActions()).doesNotContain(PedidoAllowedAction.MARK_DELIVERED);
        assertThat(caps.actionReasons()).containsEntry(
                PedidoAllowedAction.MARK_DELIVERED,
                "Pedido não possui subpedidos elegíveis para esta ação."
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

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

    private TenantContext ctx(TenantUserRole role) {
        return new TenantContext(1L, "tenant-demo", 10L, Set.of(role.name()), TenantResolutionSource.JWT, false, false);
    }
}
