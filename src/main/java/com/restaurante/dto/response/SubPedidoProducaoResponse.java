package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SubPedidoProducaoResponse {
    private Long id;
    private String numero;
    private StatusSubPedido status;

    private Long pedidoId;
    private String pedidoNumero;

    private Long unidadeProducaoId;
    private String unidadeProducaoNome;
    private String unidadeProducaoCodigo;

    private Long mesaId;
    private String mesaReferencia;
    private Integer mesaNumero;

    private BigDecimal total;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    private List<Item> itens;

    @Data
    @Builder
    public static class Item {
        private Long produtoId;
        private String produtoNome;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
        private String observacoes;
    }
}

