package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierReliabilityLevel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "courier_reliability_profiles", indexes = {
        @Index(name = "uq_courier_reliability_profile", columnList = "courier_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierReliabilityProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @Column(name = "score", nullable = false)
    private Integer score = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 40)
    private CourierReliabilityLevel level = CourierReliabilityLevel.EXCELLENT;

    @Column(name = "total_invites", nullable = false)
    private Integer totalInvites = 0;

    @Column(name = "accepted_invites", nullable = false)
    private Integer acceptedInvites = 0;

    @Column(name = "rejected_invites", nullable = false)
    private Integer rejectedInvites = 0;

    @Column(name = "missed_invites", nullable = false)
    private Integer missedInvites = 0;

    @Column(name = "expired_invites", nullable = false)
    private Integer expiredInvites = 0;

    @Column(name = "completed_deliveries", nullable = false)
    private Integer completedDeliveries = 0;

    @Column(name = "cancelled_after_accept_count", nullable = false)
    private Integer cancelledAfterAcceptCount = 0;

    @Column(name = "no_show_count", nullable = false)
    private Integer noShowCount = 0;

    @Column(name = "failed_delivery_count", nullable = false)
    private Integer failedDeliveryCount = 0;

    @Column(name = "last_penalty_at")
    private LocalDateTime lastPenaltyAt;

    @Column(name = "suspension_until")
    private LocalDateTime suspensionUntil;
}
