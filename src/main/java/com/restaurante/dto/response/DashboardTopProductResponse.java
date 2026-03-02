package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para resposta de produtos mais vendidos do dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTopProductResponse {
    private Long produtoId;
    private String nome;
    private Integer quantidadeVendida;
    private BigDecimal valorTotal;
    private String categoria;
}
