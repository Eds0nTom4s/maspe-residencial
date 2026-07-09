package com.restaurante.financeiro.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetodoPagamentoAppyPayTest {

    @Test
    void deveAceitarAliasesDoFrontend() {
        assertEquals(MetodoPagamentoAppyPay.GPO, MetodoPagamentoAppyPay.from("MULTICAIXA_EXPRESS"));
        assertEquals(MetodoPagamentoAppyPay.GPO, MetodoPagamentoAppyPay.from("GPO"));
        assertEquals(MetodoPagamentoAppyPay.REF, MetodoPagamentoAppyPay.from("REFERENCIA"));
        assertEquals(MetodoPagamentoAppyPay.REF, MetodoPagamentoAppyPay.from("REF"));
    }

    @Test
    void deveRejeitarMetodoDesconhecido() {
        assertThrows(IllegalArgumentException.class, () -> MetodoPagamentoAppyPay.from("PIX"));
    }
}
