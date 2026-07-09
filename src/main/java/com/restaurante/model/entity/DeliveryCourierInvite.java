package com.restaurante.model.entity;

import com.restaurante.model.enums.DeliveryCourierInviteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_courier_invites", indexes = {
        @Index(name = "idx_delivery_invite_job_status", columnList = "tenant_id, delivery_job_id, status"),
        @Index(name = "idx_delivery_invite_courier_status", columnList = "tenant_id, courier_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeliveryCourierInvite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_job_id", nullable = false)
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DeliveryCourierInviteStatus status = DeliveryCourierInviteStatus.PENDING;

    @Column(name = "distance_to_pickup_km", precision = 9, scale = 3)
    private BigDecimal distanceToPickupKm;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;
}

