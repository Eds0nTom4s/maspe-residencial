package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubPedidoResponse {

    private Long id;
    private Long pedidoId;
    private String numeroPedido;
    private Long cozinhaId;
    private String nomeCozinha;
    private Long unidadeAtendimentoId;
    private String nomeUnidadeAtendimento;
    private StatusSubPedido status;
    private List<ItemPedidoResponse> itens;
    private String observacoes;
    private LocalDateTime recebidoEm;
    private LocalDateTime iniciadoEm;
    private LocalDateTime prontoEm;
    private LocalDateTime entregueEm;
    private Long tempoPreparacaoMinutos;
}
