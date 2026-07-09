package com.restaurante.model.enums;

public enum OrderFulfillmentStatus {
    DRAFT,
    REQUESTED,
    WAITING_PAYMENT,
    READY_FOR_PICKUP,
    SEARCHING_COURIER,
    COURIER_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    FAILED
}

