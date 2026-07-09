package com.restaurante.financeiro.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class OrdemPagamentoTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public String gerarTokenQr() {
        // token não-enumerável, usado em URL/QR (80 chars max no DB)
        return "OP-" + randomBase36(32) + "-" + randomBase36(12);
    }

    public String gerarCodigoCurto() {
        // exibível ao cliente/operador; não é segredo, mas reduz fricção
        return "OP" + randomBase36(8);
    }

    private static String randomBase36(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = BASE36[SECURE_RANDOM.nextInt(BASE36.length)];
        }
        return new String(out);
    }
}

