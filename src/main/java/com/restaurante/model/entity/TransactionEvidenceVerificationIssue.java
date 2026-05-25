package com.restaurante.model.entity;

import com.restaurante.model.enums.TransactionEvidenceVerificationIssueType;
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
@Table(name = "transaction_evidence_verification_issues", indexes = {
        @Index(name = "idx_transaction_evidence_verification_issues", columnList = "tenant_id, verification_run_id, issue_type")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionEvidenceVerificationIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_run_id", nullable = false)
    private TransactionEvidenceVerificationRun verificationRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private TransactionEvidenceEvent event;

    @Column(name = "ledger_sequence")
    private Long ledgerSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 40)
    private TransactionEvidenceVerificationIssueType issueType;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();
}

