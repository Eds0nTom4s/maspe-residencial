package com.restaurante.exception;

import com.restaurante.model.enums.StatusSubPedido;

public class OrderNotAcceptedForProductionException extends ConflictException {

    private final String code;
    private final StatusSubPedido currentStatus;

    public OrderNotAcceptedForProductionException(String message,
                                                  String code,
                                                  StatusSubPedido currentStatus) {
        super(message);
        this.code = code;
        this.currentStatus = currentStatus;
    }

    public String getCode() {
        return code;
    }

    public StatusSubPedido getCurrentStatus() {
        return currentStatus;
    }
}
