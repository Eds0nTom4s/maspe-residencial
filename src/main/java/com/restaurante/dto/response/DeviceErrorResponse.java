package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record DeviceErrorResponse(
        DeviceErrorCode code,
        String message,
        boolean recoverable,
        DeviceRecoveryAction action,
        LocalDateTime serverTime,
        Map<String, Object> details
) {

    public enum DeviceErrorCode {
        DEVICE_UNAUTHORIZED,
        DEVICE_TOKEN_INVALID,
        DEVICE_FORBIDDEN,
        DEVICE_SUSPENDED,
        DEVICE_REVOKED,
        DEVICE_ACTIVATION_CODE_INVALID,
        DEVICE_ACTIVATION_CODE_EXPIRED,
        DEVICE_INTERNAL_ERROR,
        DEVICE_REQUEST_INVALID
    }

    public enum DeviceRecoveryAction {
        RETRY,
        REAUTH_DEVICE,
        CONTACT_SUPPORT,
        NONE
    }
}

