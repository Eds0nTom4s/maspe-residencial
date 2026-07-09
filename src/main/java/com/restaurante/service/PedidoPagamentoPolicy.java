package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.service.operacional.OperationalTemplatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PedidoPagamentoPolicy {

    private final OperationalTemplatePolicy operationalTemplatePolicy;

    public enum PaymentFlow {
        PUBLIC_QR_GATEWAY,
        PUBLIC_QR_MANUAL_ORDER,
        MANUAL_CONFIRMATION,
        GATEWAY_CONFIRMATION,
        TENANT_MANUAL_CONFIRMATION,
        DEVICE_POS
    }

    public void assertPodeIniciarPagamento(Pedido pedido, PaymentFlow flow) {
        assertPedidoValido(pedido);
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new BusinessException("Pedido já está pago.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new BusinessException("Pedido cancelado não permite pagamento.");
        }
        if (exigeAceiteAntesDoPagamento(pedido, flow) && !pedidoEstaAceiteParaPagamento(pedido)) {
            throw new BusinessException("Pagamento disponível apenas após aceite do pedido.");
        }
    }

    public void assertPodeConfirmarPagamento(Pedido pedido, PaymentFlow flow) {
        assertPedidoValido(pedido);
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new BusinessException("Pedido já está pago.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new BusinessException("Pedido cancelado não permite confirmação de pagamento.");
        }
        if (exigeAceiteAntesDoPagamento(pedido, flow) && !pedidoEstaAceiteParaPagamento(pedido)) {
            throw new BusinessException("Pagamento só pode ser confirmado após aceite do pedido.");
        }
    }

    public boolean exigeAceiteAntesDoPagamento(Pedido pedido, PaymentFlow flow) {
        return operationalTemplatePolicy.requiresAcceptanceBeforePayment(pedido, resolveActor(flow));
    }

    public boolean pedidoEstaAceiteParaPagamento(Pedido pedido) {
        if (pedido == null || pedido.getStatus() == null) {
            return false;
        }
        return pedido.getStatus() == StatusPedido.EM_ANDAMENTO || pedido.getStatus() == StatusPedido.FINALIZADO;
    }

    private void assertPedidoValido(Pedido pedido) {
        if (pedido == null) {
            throw new BusinessException("Pedido inválido para pagamento.");
        }
    }

    private OperationalOrigem resolveActor(PaymentFlow flow) {
        return switch (flow) {
            case DEVICE_POS -> OperationalOrigem.DEVICE_POS;
            case GATEWAY_CONFIRMATION -> OperationalOrigem.GATEWAY;
            case TENANT_MANUAL_CONFIRMATION, MANUAL_CONFIRMATION -> OperationalOrigem.TENANT_CASHIER;
            case PUBLIC_QR_GATEWAY, PUBLIC_QR_MANUAL_ORDER -> OperationalOrigem.QR_PUBLICO;
        };
    }
}
