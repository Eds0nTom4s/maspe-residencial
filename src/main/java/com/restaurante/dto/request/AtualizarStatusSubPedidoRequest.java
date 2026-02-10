package com.restaurante.dto.request;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtualizarStatusSubPedidoRequest {

    @NotNull(message = "Status é obrigatório")
    private StatusSubPedido novoStatus;

    private String observacoes;
}
