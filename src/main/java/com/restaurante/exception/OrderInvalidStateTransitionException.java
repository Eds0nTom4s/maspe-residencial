package com.restaurante.exception;

import com.restaurante.model.enums.StatusPedido;

public class OrderInvalidStateTransitionException extends ConflictException {

    private final String code;
    private final StatusPedido currentStatus;
    private final String requestedTransition;

    public OrderInvalidStateTransitionException(String message,
                                                String code,
                                                StatusPedido currentStatus,
                                                String requestedTransition) {
        super(message);
        this.code = code;
        this.currentStatus = currentStatus;
        this.requestedTransition = requestedTransition;
    }

    public String getCode() {
        return code;
    }

    public StatusPedido getCurrentStatus() {
        return currentStatus;
    }

    public String getRequestedTransition() {
        return requestedTransition;
    }
}
