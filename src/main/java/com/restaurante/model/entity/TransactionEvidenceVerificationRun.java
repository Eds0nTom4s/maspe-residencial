package com.restaurante.model.entity;

import com.restaurante.model.enums.TransactionEvidenceVerificationRunStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_evidence_verification_runs", indexes = {
        @Index(name = "idx_transaction_evidence_verification_runs", columnList = "tenant_id, started_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionEvidenceVerificationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionEvidenceVerificationRunStatus status = TransactionEvidenceVerificationRunStatus.RUNNING;

    @Column(name = "checked_events_count", nullable = false)
    private Integer checkedEventsCount = 0;

    @Column(name = "invalid_events_count", nullable = false)
    private Integer invalidEventsCount = 0;

    @Column(name = "broken_chain_count", nullable = false)
    private Integer brokenChainCount = 0;

    @Column(name = "sequence_gap_count", nullable = false)
    private Integer sequenceGapCount = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "report_hash", length = 128)
    private String reportHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;
}

