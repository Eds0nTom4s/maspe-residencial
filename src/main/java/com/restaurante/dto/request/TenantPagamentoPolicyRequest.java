package com.restaurante.dto.request;

import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TenantPagamentoPolicyRequest {

    @NotNull
    private Boolean pagamentoObrigatorioAntesDoPedido;

    @NotNull
    private Boolean permitirPedidoSemPagamento;

    @NotNull
    private Boolean permitirPosPago;

    @NotNull
    private Boolean permitirCash;

    @NotNull
    private Boolean permitirPagamentoNaEntrega;

    @NotNull
    @Min(1)
    @Max(1440)
    private Integer tempoExpiracaoPedidoPendentePagamentoMinutos;

    @NotNull
    private ComportamentoPedidoNaoPago comportamentoPedidoNaoPago;
}
