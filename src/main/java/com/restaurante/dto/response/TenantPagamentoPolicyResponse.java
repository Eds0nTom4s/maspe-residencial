package com.restaurante.dto.response;

import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantPagamentoPolicyResponse {
    private Long tenantId;
    private boolean pagamentoObrigatorioAntesDoPedido;
    private boolean permitirPedidoSemPagamento;
    private boolean permitirPosPago;
    private boolean permitirCash;
    private boolean permitirPagamentoNaEntrega;
    private Integer tempoExpiracaoPedidoPendentePagamentoMinutos;
    private ComportamentoPedidoNaoPago comportamentoPedidoNaoPago;
    private LocalDateTime configuradoEm;
}
