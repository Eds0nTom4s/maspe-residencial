package com.restaurante.model.entity;

import com.restaurante.model.enums.TransactionEvidenceLedgerStateStatus;
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
@Table(name = "transaction_evidence_ledger_states", indexes = {
        @Index(name = "uq_transaction_evidence_ledger_states", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionEvidenceLedgerState extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence = 0L;

    @Column(name = "last_event_hash", length = 128)
    private String lastEventHash;

    @Column(name = "last_event_id")
    private Long lastEventId;

    @Column(name = "last_recorded_at")
    private LocalDateTime lastRecordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionEvidenceLedgerStateStatus status = TransactionEvidenceLedgerStateStatus.ACTIVE;
}

