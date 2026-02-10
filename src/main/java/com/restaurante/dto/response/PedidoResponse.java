package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de resposta completa para Pedido
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoResponse {

    private Long id;
    private String numero;
    private StatusPedido status;
    private String observacoes;
    private BigDecimal total;
    private Long unidadeConsumoId;
    private String referenciaUnidadeConsumo;
    private List<ItemPedidoResponse> itens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
