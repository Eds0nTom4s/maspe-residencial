package com.restaurante.security.tenant;

public enum TenantResolutionSource {
    JWT,
    PLATFORM_ADMIN_SELECTION,
    QR_TOKEN,
    DEVICE_TOKEN,
    PAYMENT_CALLBACK,
    LEGACY_NONE
}

