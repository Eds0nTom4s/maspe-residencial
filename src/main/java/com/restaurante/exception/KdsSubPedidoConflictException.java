package com.restaurante.exception;

import com.restaurante.model.enums.StatusSubPedido;

public class KdsSubPedidoConflictException extends RuntimeException {

    private final StatusSubPedido currentStatus;
    private final Long currentVersion;

    public KdsSubPedidoConflictException(String message, StatusSubPedido currentStatus, Long currentVersion) {
        super(message);
        this.currentStatus = currentStatus;
        this.currentVersion = currentVersion;
    }

    public StatusSubPedido getCurrentStatus() {
        return currentStatus;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
