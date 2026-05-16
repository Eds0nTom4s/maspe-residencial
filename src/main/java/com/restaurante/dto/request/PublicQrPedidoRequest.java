package com.restaurante.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicQrPedidoRequest {

    /**
     * Chave de idempotência opcional no body (o header Idempotency-Key tem prioridade).
     */
    private String idempotencyKey;

    private String clienteNome;
    private String clienteTelefone;
    private String observacao;

    @NotEmpty(message = "Pedido deve conter ao menos um item")
    @Valid
    private List<PublicQrPedidoItemRequest> itens;
}
