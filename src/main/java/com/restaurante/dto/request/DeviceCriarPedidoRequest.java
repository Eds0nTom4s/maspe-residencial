package com.restaurante.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DeviceCriarPedidoRequest {
    @NotBlank
    private String clientRequestId;
    private Long mesaId;
    private Long qrCodeId;
    private String observacao;
    @Valid
    @NotEmpty
    private List<DeviceCriarPedidoItemRequest> itens;
}

