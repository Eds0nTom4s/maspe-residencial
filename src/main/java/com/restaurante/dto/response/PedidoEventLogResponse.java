package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoEventLogResponse {

    private Long id;
    private Long pedidoId;
    private String numeroPedido;
    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private String usuario;
    private LocalDateTime timestamp;
    private String observacoes;
    private String descricao;
}
