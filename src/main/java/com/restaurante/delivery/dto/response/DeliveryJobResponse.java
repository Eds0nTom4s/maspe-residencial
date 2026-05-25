package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.DeliveryFeePaymentStatus;
import com.restaurante.model.enums.DeliveryJobStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
public class DeliveryJobResponse {
    Long id;
    Long pedidoId;
    Long fulfillmentId;
    Long courierId;
    DeliveryJobStatus status;
    String pickupAddressText;
    String deliveryAddressText;
    BigDecimal estimatedDistanceKm;
    BigDecimal estimatedDeliveryFee;
    BigDecimal finalDeliveryFee;
    String deliveryFeeCurrency;
    DeliveryFeePaymentStatus deliveryFeePaymentStatus;
    LocalDateTime requestedAt;
    LocalDateTime assignedAt;
    LocalDateTime acceptedAt;
    LocalDateTime pickedUpAt;
    LocalDateTime deliveredAt;
    LocalDateTime cancelledAt;
    LocalDateTime failedAt;
}

