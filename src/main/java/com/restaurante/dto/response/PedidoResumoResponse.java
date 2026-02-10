package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta resumida para Pedido (usado em listas)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoResumoResponse {

    private Long id;
    private String numero;
    private StatusPedido status;
    private BigDecimal total;
    private Integer quantidadeItens;
    private LocalDateTime createdAt;
}
