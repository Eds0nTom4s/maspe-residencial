package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierPenaltySeverity;
import com.restaurante.model.enums.CourierPenaltyStatus;
import com.restaurante.model.enums.CourierPenaltyType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "courier_penalty_events")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierPenaltyEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_job_id")
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_id")
    private DeliveryCourierInvite invite;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private CourierPenaltyType penaltyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 40)
    private CourierPenaltySeverity severity;

    @Column(name = "points_delta", nullable = false)
    private Integer pointsDelta;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CourierPenaltyStatus status = CourierPenaltyStatus.ACTIVE;
}
