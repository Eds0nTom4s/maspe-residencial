package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicQrPedidoResponse {

    private Long pedidoId;
    private String numero;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido statusFinanceiro;

    private String tenantNome;
    private String instituicaoNome;
    private String unidadeAtendimentoNome;
    private String mesaReferencia;
    private Integer mesaNumero;

    private BigDecimal total;
    private List<PublicQrPedidoItemResponse> itens;
    private PaymentOrderResponse paymentOrder;

    private String mensagem;
}
