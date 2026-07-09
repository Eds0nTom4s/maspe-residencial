package com.restaurante.financeiro.polling;

public interface PaymentGatewayStatusPort {
    GatewayPaymentStatusResponse consultarStatus(String gatewayChargeId, String externalReference);
}

