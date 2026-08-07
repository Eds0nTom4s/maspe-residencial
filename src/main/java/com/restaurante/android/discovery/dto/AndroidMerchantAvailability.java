package com.restaurante.android.discovery.dto;

/** Public availability vocabulary. UNKNOWN is mandatory while no canonical schedule exists. */
public enum AndroidMerchantAvailability {
    OPEN,
    CLOSING_SOON,
    OPENS_AT,
    CLOSED,
    UNKNOWN
}
