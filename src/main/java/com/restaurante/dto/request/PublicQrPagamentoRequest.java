package com.restaurante.dto.request;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicQrPagamentoRequest {

    /**
     * Chave de idempotência opcional no body (header Idempotency-Key tem prioridade).
     */
    private String idempotencyKey;

    @NotNull(message = "metodoPagamento é obrigatório")
    private MetodoPagamentoAppyPay metodoPagamento;

    /**
     * Telefone do cliente (opcional). Usado para M-Express (GPO) quando aplicável.
     */
    private String telefone;
}

