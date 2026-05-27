package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courier_penalty_policies")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierPenaltyPolicy extends BaseEntity {

    @Column(name = "status", nullable = false, length = 40)
    private String status = "ACTIVE";

    @Column(name = "missed_invites_24h_pause_threshold", nullable = false)
    private Integer missedInvites24hPauseThreshold = 3;

    @Column(name = "missed_invites_24h_pause_minutes", nullable = false)
    private Integer missedInvites24hPauseMinutes = 30;

    @Column(name = "missed_invites_7d_warning_threshold", nullable = false)
    private Integer missedInvites7dWarningThreshold = 5;

    @Column(name = "missed_invites_7d_suspension_threshold", nullable = false)
    private Integer missedInvites7dSuspensionThreshold = 10;

    @Column(name = "missed_invites_7d_suspension_hours", nullable = false)
    private Integer missedInvites7dSuspensionHours = 24;

    @Column(name = "no_show_7d_suspension_threshold", nullable = false)
    private Integer noShow7dSuspensionThreshold = 2;

    @Column(name = "no_show_7d_suspension_hours", nullable = false)
    private Integer noShow7dSuspensionHours = 48;

    @Column(name = "low_priority_score_threshold", nullable = false)
    private Integer lowPriorityScoreThreshold = 74;

    @Column(name = "warned_score_threshold", nullable = false)
    private Integer warnedScoreThreshold = 59;

    @Column(name = "suspended_score_threshold", nullable = false)
    private Integer suspendedScoreThreshold = 40;
}
