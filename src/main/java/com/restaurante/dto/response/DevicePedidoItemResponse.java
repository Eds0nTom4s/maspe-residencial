package com.restaurante.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DevicePedidoItemResponse {
    private Long itemPedidoId;
    private Long produtoId;
    private String produtoNome;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
    private String observacao;
}

