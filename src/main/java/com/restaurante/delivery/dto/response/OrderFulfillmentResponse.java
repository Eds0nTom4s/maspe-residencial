package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.FulfillmentType;
import com.restaurante.model.enums.OrderFulfillmentStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
public class OrderFulfillmentResponse {
    Long id;
    Long pedidoId;
    FulfillmentType fulfillmentType;
    OrderFulfillmentStatus status;
    String customerName;
    String customerPhoneMasked;
    String deliveryAddressText;
    BigDecimal deliveryLatitude;
    BigDecimal deliveryLongitude;
    String deliveryNotes;
    LocalDateTime deliveryRequestedAt;
    LocalDateTime pickupReadyAt;
    LocalDateTime completedAt;
    LocalDateTime cancelledAt;
    String cancellationReason;
}

