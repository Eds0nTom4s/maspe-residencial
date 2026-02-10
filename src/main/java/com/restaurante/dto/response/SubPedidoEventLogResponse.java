package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubPedidoEventLogResponse {

    private Long id;
    private Long subPedidoId;
    private Long pedidoId;
    private String numeroPedido;
    private Long cozinhaId;
    private String nomeCozinha;
    private StatusSubPedido statusAnterior;
    private StatusSubPedido statusNovo;
    private String usuario;
    private LocalDateTime timestamp;
    private String observacoes;
    private Long tempoTransacaoMs;
    private String descricao;
    private Boolean transicaoCritica;
}
