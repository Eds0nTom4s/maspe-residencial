package com.restaurante.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TurnoPendenciaResponse {
    private String categoria;
    private Long pedidoId;
    private String pedidoNumero;
    private Long sessaoId;
    private Long subPedidoId;
    private String status;
    private String acaoRecomendada;
}
