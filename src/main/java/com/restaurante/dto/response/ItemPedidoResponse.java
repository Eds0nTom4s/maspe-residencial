package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de resposta para Item de Pedido
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedidoResponse {

    private Long id;
    private Long produtoId;
    private String produtoNome;
    private String produtoCodigo;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private String observacoes;
}
