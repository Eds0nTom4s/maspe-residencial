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
        DEVICE_ORDER_IDEMPOTENCY_KEY_REQUIRED,
        DEVICE_ORDER_CLIENT_REQUEST_ID_REQUIRED,
        DEVICE_ORDER_IDEMPOTENCY_CONFLICT,
        DEVICE_ORDER_TURNO_REQUIRED,
        DEVICE_ORDER_PRODUCT_NOT_FOUND,
        DEVICE_ORDER_PRODUCT_UNAVAILABLE,
        DEVICE_ORDER_MESA_NOT_FOUND,
        DEVICE_ORDER_MESA_SCOPE_INVALID,
        DEVICE_ORDER_QR_INVALID,
        DEVICE_ORDER_EMPTY_ITEMS,
        DEVICE_ORDER_INVALID_QUANTITY,
        DEVICE_ORDER_VALIDATION_FAILED,
        DEVICE_ORDER_CREATE_FORBIDDEN,
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
