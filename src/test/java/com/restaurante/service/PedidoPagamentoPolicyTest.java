package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.service.operacional.OperationalTemplatePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoPagamentoPolicyTest {

    private final PedidoPagamentoPolicy policy = new PedidoPagamentoPolicy(new OperationalTemplatePolicy());

    @Test
    void qrMesaAndQrPrincipalPaymentRequiresOperationalAcceptance() {
        Pedido mesa = pedido("CONSUMA_REST", 1, true, StatusPedido.CRIADO);
        Pedido principal = pedido("CONSUMA_REST_V1", null, false, StatusPedido.CRIADO);

        assertThatThrownBy(() -> policy.assertPodeIniciarPagamento(mesa, PedidoPagamentoPolicy.PaymentFlow.PUBLIC_QR_GATEWAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pagamento disponível apenas após aceite do pedido");

        assertThatThrownBy(() -> policy.assertPodeIniciarPagamento(principal, PedidoPagamentoPolicy.PaymentFlow.PUBLIC_QR_GATEWAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pagamento disponível apenas após aceite do pedido");
    }

    @Test
    void acceptedPublicQrPedidoCanStartPayment() {
        Pedido pedido = pedido("CONSUMA_REST", 1, false, StatusPedido.EM_ANDAMENTO);

        assertThatCode(() -> policy.assertPodeIniciarPagamento(pedido, PedidoPagamentoPolicy.PaymentFlow.PUBLIC_QR_GATEWAY))
                .doesNotThrowAnyException();
    }

    @Test
    void consumaPontoDevicePosCanStartImmediatePayment() {
        Pedido pedido = pedido("CONSUMA_PONTO", 1, false, StatusPedido.CRIADO);

        assertThatCode(() -> policy.assertPodeIniciarPagamento(pedido, PedidoPagamentoPolicy.PaymentFlow.DEVICE_POS))
                .doesNotThrowAnyException();
    }

    @Test
    void gatewayConfirmationForCreatedPublicPedidoRequiresAcceptance() {
        Pedido pedido = pedido("CONSUMA_REST", 1, false, StatusPedido.CRIADO);

        assertThatThrownBy(() -> policy.assertPodeConfirmarPagamento(pedido, PedidoPagamentoPolicy.PaymentFlow.GATEWAY_CONFIRMATION))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pagamento só pode ser confirmado após aceite do pedido");
    }

    private Pedido pedido(String templateCode, Integer templateVersion, boolean mesa, StatusPedido status) {
        Tenant tenant = new Tenant();
        tenant.setTemplateCode(templateCode);
        tenant.setTemplateVersion(templateVersion);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setTenant(tenant);
        sessao.setQrCodeSessao("qr-" + templateCode + "-" + mesa);
        if (mesa) {
            Mesa mesaEntity = new Mesa();
            mesaEntity.setReferencia("Mesa 1");
            mesaEntity.setTenant(tenant);
            sessao.setMesa(mesaEntity);
        }

        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setSessaoConsumo(sessao);
        pedido.setStatus(status);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        return pedido;
    }
}
