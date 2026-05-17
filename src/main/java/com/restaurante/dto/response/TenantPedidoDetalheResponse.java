package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPedidoDetalheResponse {
    private Long id;
    private String numero;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido statusFinanceiro;
    private BigDecimal total;

    private String observacoes;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime pagoEm;

    private TenantPedidoContextResponse contexto;

    @Builder.Default
    private List<ItemResponse> itens = new ArrayList<>();

    @Builder.Default
    private List<SubPedidoResponse> subPedidos = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantPedidoContextResponse {
        private Long instituicaoId;
        private String instituicaoNome;
        private Long unidadeAtendimentoId;
        private String unidadeAtendimentoNome;
        private Long mesaId;
        private String mesaReferencia;
        private Integer mesaNumero;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemResponse {
        private Long produtoId;
        private String produtoNome;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
        private String observacao;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubPedidoResponse {
        private Long id;
        private String status;
        @Builder.Default
        private List<ItemResponse> itens = new ArrayList<>();
    }
}

