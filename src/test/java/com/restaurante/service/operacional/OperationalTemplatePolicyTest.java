package com.restaurante.service.operacional;

import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalOrigem;
import org.junit.jupiter.api.Test;

import static com.restaurante.service.operacional.OperationalTemplatePolicy.PedidoOrigem.CAIXA;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.PedidoOrigem.DEVICE_POS;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.PedidoOrigem.PDV_INTERNO;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.PedidoOrigem.QR_MESA;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.PedidoOrigem.QR_PRINCIPAL;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.ProductionFlow.NONE;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.ProductionFlow.OPTIONAL;
import static com.restaurante.service.operacional.OperationalTemplatePolicy.ProductionFlow.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

class OperationalTemplatePolicyTest {

    private final OperationalTemplatePolicy policy = new OperationalTemplatePolicy();

    @Test
    void qrMesaAndQrPrincipalRequireAcceptanceBeforePayment() {
        Pedido mesa = pedido("CONSUMA_REST", 1, true);
        Pedido principal = pedido("CONSUMA_REST_V1", null, false);

        assertThat(policy.resolvePedidoOrigem(mesa, OperationalOrigem.QR_PUBLICO)).isEqualTo(QR_MESA);
        assertThat(policy.requiresAcceptanceBeforePayment(mesa, OperationalOrigem.QR_PUBLICO)).isTrue();

        assertThat(policy.resolvePedidoOrigem(principal, OperationalOrigem.QR_PUBLICO)).isEqualTo(QR_PRINCIPAL);
        assertThat(policy.requiresAcceptanceBeforePayment(principal, OperationalOrigem.QR_PUBLICO)).isTrue();
    }

    @Test
    void consumaRestPublicPedidoRequiresAcceptanceBeforePayment() {
        Pedido pedido = pedido("CONSUMA_REST", 1, false);

        assertThat(policy.resolveTemplateCode(pedido)).isEqualTo(OperationalTemplatePolicy.TEMPLATE_REST_V1);
        assertThat(policy.requiresAcceptanceBeforePayment(pedido, OperationalOrigem.QR_PUBLICO)).isTrue();
    }

    @Test
    void consumaPontoKeepsPublicQrGuardButAllowsDeviceImmediatePayment() {
        Pedido publicQr = pedido("CONSUMA_PONTO", 1, false);
        Pedido device = pedido("CONSUMA_PONTO", 1, false);

        assertThat(policy.requiresAcceptanceBeforePayment(publicQr, OperationalOrigem.QR_PUBLICO)).isTrue();
        assertThat(policy.resolvePedidoOrigem(device, OperationalOrigem.DEVICE_POS)).isEqualTo(DEVICE_POS);
        assertThat(policy.requiresAcceptanceBeforePayment(device, OperationalOrigem.DEVICE_POS)).isFalse();
        assertThat(policy.allowsImmediatePayment(OperationalTemplatePolicy.TEMPLATE_PONTO_V1, DEVICE_POS, OperationalOrigem.DEVICE_POS)).isTrue();
    }

    @Test
    void pdvInternoIsFutureImmediatePaymentOnlyForAuthenticatedOperatorActors() {
        assertThat(policy.allowsImmediatePayment(OperationalTemplatePolicy.TEMPLATE_PDV_INTERNO, PDV_INTERNO, OperationalOrigem.TENANT_OPERATOR)).isTrue();
        assertThat(policy.allowsImmediatePayment(OperationalTemplatePolicy.TEMPLATE_PDV_INTERNO, PDV_INTERNO, OperationalOrigem.QR_PUBLICO)).isFalse();
        assertThat(policy.requiresAcceptanceBeforePayment(OperationalTemplatePolicy.TEMPLATE_PDV_INTERNO, PDV_INTERNO)).isFalse();
    }

    @Test
    void kdsCannotConfirmPaymentAndCaixaCannotMoveProduction() {
        assertThat(policy.canConfirmPayment(OperationalOrigem.DEVICE_KDS, OperationalTemplatePolicy.TEMPLATE_KDS, QR_MESA)).isFalse();
        assertThat(policy.canConfirmPayment(OperationalOrigem.TENANT_KITCHEN, OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_MESA)).isFalse();

        assertThat(policy.canMoveProduction(OperationalOrigem.TENANT_CASHIER, OperationalTemplatePolicy.TEMPLATE_CAIXA, CAIXA)).isFalse();
        assertThat(policy.canConfirmPayment(OperationalOrigem.TENANT_CASHIER, OperationalTemplatePolicy.TEMPLATE_CAIXA, CAIXA)).isTrue();
    }

    @Test
    void acceptRejectAreTenantOperationalCommandsNotKdsCommands() {
        assertThat(policy.canAccept(OperationalOrigem.TENANT_OPERATOR, OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_PRINCIPAL)).isTrue();
        assertThat(policy.canReject(OperationalOrigem.TENANT_OPERATOR, OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_PRINCIPAL)).isTrue();
        assertThat(policy.canAccept(OperationalOrigem.DEVICE_KDS, OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_PRINCIPAL)).isFalse();
        assertThat(policy.canReject(OperationalOrigem.TENANT_KITCHEN, OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_PRINCIPAL)).isFalse();
    }

    @Test
    void productionAndTurnoMatrixIsExplicitByTemplate() {
        assertThat(policy.requiresTurno(OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_MESA)).isTrue();
        assertThat(policy.requiresTurno(OperationalTemplatePolicy.TEMPLATE_PONTO_V1, DEVICE_POS)).isTrue();

        assertThat(policy.productionFlow(OperationalTemplatePolicy.TEMPLATE_REST_V1, QR_MESA)).isEqualTo(REQUIRED);
        assertThat(policy.productionFlow(OperationalTemplatePolicy.TEMPLATE_PONTO_V1, DEVICE_POS)).isEqualTo(OPTIONAL);
        assertThat(policy.productionFlow(OperationalTemplatePolicy.TEMPLATE_CAIXA, CAIXA)).isEqualTo(NONE);
    }

    private Pedido pedido(String templateCode, Integer templateVersion, boolean mesa) {
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
        return pedido;
    }
}
