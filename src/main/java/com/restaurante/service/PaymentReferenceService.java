package com.restaurante.service;

import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class PaymentReferenceService {

    // AppyPay merchantTransactionId limit
    public static final int MAX_LEN = 15;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final PagamentoGatewayRepository pagamentoGatewayRepository;

    /**
     * Gera externalReference compacta e tenant-aware (<= 15 chars), adequada ao AppyPay.
     *
     * Formato (15):
     * - TTTT: hash compacto do tenantCode (base36, estável)
     * - Q: tipo (Pedido QR)
     * - SSSSSS: base36(time mod)
     * - RRRR: random base36
     */
    public String gerarReferenciaPedidoQr(Tenant tenant) {
        String tenantPart = compactTenantCode(tenant.getTenantCode(), 4);
        char type = 'Q';

        for (int i = 0; i < 30; i++) {
            String timePart = leftPadBase36(System.currentTimeMillis(), 6);
            String rndPart = randomBase36(4);
            String ref = (tenantPart + type + timePart + rndPart);
            if (ref.length() > MAX_LEN) {
                ref = ref.substring(0, MAX_LEN);
            }
            if (!pagamentoGatewayRepository.existsByExternalReference(ref)) {
                return ref;
            }
        }
        throw new IllegalStateException("Falha ao gerar referência externa única para pagamento.");
    }

    public static String compactTenantCode(String tenantCode, int len) {
        if (tenantCode == null) tenantCode = "";
        String normalized = tenantCode.trim().toUpperCase();
        String alnum = normalized.replaceAll("[^A-Z0-9]", "");
        if (alnum.length() >= len) return alnum.substring(0, len);
        return hashBase36Padded(normalized, len);
    }

    private static String hashBase36Padded(String input, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 6; i++) { // 48 bits
                v = (v << 8) | (digest[i] & 0xFFL);
            }
            String s = toBase36(v);
            if (s.length() >= len) return s.substring(0, len);
            return "0".repeat(len - s.length()) + s;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao compactar tenantCode.", e);
        }
    }

    private static String leftPadBase36(long value, int len) {
        long mod = 1;
        for (int i = 0; i < len; i++) mod *= 36;
        long v = Math.floorMod(value, mod);
        String s = toBase36(v);
        if (s.length() >= len) return s.substring(s.length() - len);
        return "0".repeat(len - s.length()) + s;
    }

    private static String randomBase36(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = BASE36[SECURE_RANDOM.nextInt(BASE36.length)];
        }
        return new String(out);
    }

    private static String toBase36(long v) {
        if (v == 0) return "0";
        StringBuilder sb = new StringBuilder();
        long x = v;
        while (x > 0) {
            int r = (int) (x % 36);
            sb.append(BASE36[r]);
            x /= 36;
        }
        return sb.reverse().toString();
    }
}

