package com.restaurante.financeiro.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppyPayStatusAdapter implements PaymentGatewayStatusPort {

    private final AppyPayClient appyPayClient;
    private final ObjectMapper objectMapper;

    @Override
    public GatewayPaymentStatusResponse consultarStatus(String gatewayChargeId, String externalReference) {
        AppyPayChargeResponse resp = appyPayClient.getCharge(gatewayChargeId);
        return GatewayPaymentStatusResponse.builder()
                .externalReference(externalReference)
                .gatewayChargeId(gatewayChargeId)
                .status(normalize(resp != null ? resp.getStatus() : null))
                .amountCents(resp != null ? resp.getAmount() : null)
                .rawStatus(resp != null ? resp.getStatus() : null)
                .rawPayload(serializeSilently(resp))
                .errorMessage(resp != null ? resp.getErrorMessage() : null)
                .build();
    }

    private GatewayPaymentStatus normalize(String raw) {
        if (raw == null) return GatewayPaymentStatus.UNKNOWN;
        String s = raw.trim().toUpperCase();
        if (s.contains("CONFIRM")) return GatewayPaymentStatus.CONFIRMED;
        if (s.contains("PEND")) return GatewayPaymentStatus.PENDING;
        if (s.contains("FAIL")) return GatewayPaymentStatus.FAILED;
        if (s.contains("EXPIRE")) return GatewayPaymentStatus.EXPIRED;
        if (s.contains("CANCEL")) return GatewayPaymentStatus.CANCELLED;
        return GatewayPaymentStatus.UNKNOWN;
    }

    private String serializeSilently(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}

