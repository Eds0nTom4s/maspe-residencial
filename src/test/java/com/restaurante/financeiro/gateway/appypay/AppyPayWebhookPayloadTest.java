package com.restaurante.financeiro.gateway.appypay;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppyPayWebhookPayloadTest {

    @Test
    void deveExtrairPayloadRealComReferenceAninhada() {
        Map<String, Object> payload = Map.of(
                "id", "pay_123",
                "merchantTransactionId", "ST123456",
                "responseStatus", Map.of(
                        "status", "Paid",
                        "reference", Map.of("referenceNumber", "999123456")
                )
        );

        assertTrue(AppyPayWebhookPayload.hasIdentifier(payload));
        assertEquals("pay_123", AppyPayWebhookPayload.extractTransactionId(payload));
        assertEquals("ST123456", AppyPayWebhookPayload.extractMerchantTransactionId(payload));
        assertEquals("999123456", AppyPayWebhookPayload.extractReference(payload));
        assertEquals("Paid", AppyPayWebhookPayload.extractStatus(payload));
    }

    @Test
    void deveNormalizarStatusesDaAppyPay() {
        assertEquals(StatusPagamentoGateway.CONFIRMADO, AppyPayStatusMapper.toGatewayStatus("CONFIRMED"));
        assertEquals(StatusPagamentoGateway.CONFIRMADO, AppyPayStatusMapper.toGatewayStatus("Paid"));
        assertEquals(StatusPagamentoGateway.CONFIRMADO, AppyPayStatusMapper.toGatewayStatus("Success"));
        assertEquals(StatusPagamentoGateway.FALHOU, AppyPayStatusMapper.toGatewayStatus("Cancelled"));
        assertEquals(StatusPagamentoGateway.PENDENTE, AppyPayStatusMapper.toGatewayStatus("Pending"));
    }
}
