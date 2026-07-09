package com.restaurante.fiscal.autoissue.event;

import com.restaurante.model.enums.FiscalAutoIssueSource;

public record PaymentConfirmedForFiscalIssueEvent(
        Long tenantId,
        Long unidadeAtendimentoIdOrNull,
        Long pedidoId,
        Long pagamentoId,
        Long sessaoConsumoIdOrNull,
        Long caixaOperadorSessionIdOrNull,
        FiscalAutoIssueSource source
) {
}

