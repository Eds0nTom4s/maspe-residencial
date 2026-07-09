package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.DeliveryCourierInviteStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
public class DeliveryInviteResponse {
    Long id;
    Long jobId;
    Long courierId;
    DeliveryCourierInviteStatus status;
    BigDecimal distanceToPickupKm;
    LocalDateTime invitedAt;
    LocalDateTime expiresAt;
    LocalDateTime respondedAt;
    String rejectionReason;
}

