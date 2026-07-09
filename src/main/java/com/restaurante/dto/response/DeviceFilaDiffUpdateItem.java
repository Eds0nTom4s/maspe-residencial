package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;

public record DeviceFilaDiffUpdateItem(
        Long subPedidoId,
        DeviceFilaDiffAction action,
        DeviceFilaRemoveReason reason,
        StatusSubPedido statusAtual,
        KdsSubPedidoResponse subPedido
) {
    public enum DeviceFilaDiffAction {
        UPSERT,
        REMOVE,
        NOOP
    }

    public enum DeviceFilaRemoveReason {
        ENTREGUE,
        CANCELADO,
        STATUS_FINAL,
        FORA_DA_FILA
    }
}

