package com.restaurante.exception;

import com.restaurante.dto.response.DeviceErrorResponse;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class DeviceApiException extends RuntimeException {

    private final HttpStatus status;
    private final DeviceErrorResponse.DeviceErrorCode code;
    private final boolean recoverable;
    private final DeviceErrorResponse.DeviceRecoveryAction action;
    private final Map<String, Object> details;

    public DeviceApiException(HttpStatus status,
                              DeviceErrorResponse.DeviceErrorCode code,
                              String message,
                              boolean recoverable,
                              DeviceErrorResponse.DeviceRecoveryAction action,
                              Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.recoverable = recoverable;
        this.action = action;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public DeviceErrorResponse.DeviceErrorCode getCode() {
        return code;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public DeviceErrorResponse.DeviceRecoveryAction getAction() {
        return action;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}

