package com.restaurante.financeiro.service;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppyPayReconciliationFingerprintTest {

    @Test
    void excluiCamposVolateisDoFingerprint() {
        AppyPayChargeResponse primeira = response();
        primeira.setPaymentUrl("https://gateway/primeira");
        primeira.setCreatedAt("2026-07-12T18:00:00Z");
        primeira.setExpiresAt("2026-07-12T18:15:00Z");

        AppyPayChargeResponse segunda = response();
        segunda.setPaymentUrl("https://gateway/segunda?nonce=novo");
        segunda.setCreatedAt("2026-07-12T18:00:01Z");
        segunda.setExpiresAt("2026-07-12T18:15:01Z");

        assertThat(AppyPayReconciliationFingerprint.sha256(primeira))
                .isEqualTo(AppyPayReconciliationFingerprint.sha256(segunda));
    }

    @Test
    void alteraFingerprintQuandoCampoDeNegocioMuda() {
        AppyPayChargeResponse primeira = response();
        AppyPayChargeResponse segunda = response();
        segunda.setStatus("FAILED");

        assertThat(AppyPayReconciliationFingerprint.sha256(primeira))
                .isNotEqualTo(AppyPayReconciliationFingerprint.sha256(segunda));
    }

    @Test
    void payloadCanonicoNormalizaEnumsEspacosEUsaComprimento() {
        AppyPayChargeResponse primeira = response();
        primeira.setStatus(" confirmed ");
        primeira.setPaymentMethod(" gpo ");

        AppyPayChargeResponse segunda = response();
        segunda.setStatus("CONFIRMED");
        segunda.setPaymentMethod("GPO");

        assertThat(AppyPayReconciliationFingerprint.canonicalPayload(primeira))
                .isEqualTo(AppyPayReconciliationFingerprint.canonicalPayload(segunda))
                .startsWith("appypay-reconciliation:v1|")
                .contains("6:status=9:CONFIRMED");
    }

    private AppyPayChargeResponse response() {
        return AppyPayChargeResponse.builder()
                .chargeId("charge-8")
                .merchantTransactionId("QAB0Q7O5CE73KL2")
                .status("CONFIRMED")
                .amount(1000L)
                .paymentMethod("GPO")
                .entity("10100")
                .reference("999123456")
                .errorMessage(null)
                .build();
    }
}
