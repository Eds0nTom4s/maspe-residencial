package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;
import lombok.Data;

import java.util.List;

@Data
public class DeviceSubPedidoResponse {
    private Long subPedidoId;
    private Long unidadeProducaoId;
    private String unidadeProducaoNome;
    private StatusSubPedido status;
    private List<DevicePedidoItemResponse> itens;
}

