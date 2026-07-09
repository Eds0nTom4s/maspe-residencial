package com.restaurante.fiscal.autoissue.service;

import org.springframework.stereotype.Service;

@Service
public class FiscalAutoIssueIdempotencyKeyService {

    public String buildKey(Long tenantId, Long pedidoId, Long pagamentoId) {
        return "tenant:" + tenantId
                + ":pedido:" + pedidoId
                + ":pagamento:" + pagamentoId
                + ":fiscal-auto-issue:v1";
    }
}

