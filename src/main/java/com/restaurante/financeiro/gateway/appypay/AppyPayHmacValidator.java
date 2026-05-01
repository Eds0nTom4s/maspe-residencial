package com.restaurante.financeiro.gateway.appypay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validador de assinatura HMAC-SHA256 para callbacks AppyPay.
 *
 * <p>A AppyPay assina o corpo do callback com {@code HMAC-SHA256(webhookSecret, rawBody)}
 * e envia o resultado em hexadecimal no header {@code X-AppyPay-Signature}.
 *
 * <p>Este componente:
 * <ul>
 *   <li>Recalcula o HMAC com o segredo configurado.</li>
 *   <li>Compara com a assinatura recebida usando comparação em tempo constante
 *       para evitar timing attacks.</li>
 *   <li>Se {@code webhookSecret} não estiver configurado e mock=true,
 *       aceita o callback (modo de desenvolvimento).</li>
 * </ul>
 *
 * <p><strong>Produção:</strong> definir {@code APPYPAY_WEBHOOK_SECRET} como variável de ambiente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppyPayHmacValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppyPayProperties properties;

    /**
     * Valida a assinatura HMAC-SHA256 de um callback AppyPay.
     *
     * @param rawBody     corpo bruto da requisição HTTP (antes de qualquer parsing)
     * @param receivedSig valor do header {@code X-AppyPay-Signature} recebido
     * @return {@code true} se a assinatura é válida; {@code false} caso contrário
     */
    public boolean validar(String rawBody, String receivedSig) {
        String secret = properties.getWebhookSecret();

        if ((receivedSig == null || receivedSig.isBlank()) && !properties.isWebhookSignatureRequired()) {
            log.warn("[HMAC] Assinatura ausente; callback aceite porque webhook-signature-required=false.");
            return true;
        }

        // Em modo mock/dev sem secret configurado, aceita qualquer callback
        if (secret == null || secret.isBlank()) {
            if (properties.isMock()) {
                log.warn("[HMAC] webhookSecret não configurado — validação ignorada em modo mock.");
                return true;
            }
            log.error("[HMAC] webhookSecret não configurado e mock=false. Rejeitando callback.");
            return false;
        }

        if (receivedSig == null || receivedSig.isBlank()) {
            log.error("[HMAC] Header X-AppyPay-Signature ausente no callback.");
            return false;
        }

        try {
            String expectedSig = calcularHmac(rawBody, secret);

            // Comparação em tempo constante para prevenir timing attacks
            boolean valido = MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    receivedSig.getBytes(StandardCharsets.UTF_8)
            );

            if (valido) {
                log.debug("[HMAC] Assinatura do callback validada com sucesso.");
            } else {
                log.error("[HMAC] Assinatura inválida. Callback possivelmente adulterado ou secret incorreto.");
            }
            return valido;

        } catch (Exception e) {
            log.error("[HMAC] Erro ao calcular/comparar assinatura: {}", e.getMessage(), e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String calcularHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
