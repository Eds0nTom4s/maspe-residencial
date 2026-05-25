package com.restaurante.model.entity;

import com.restaurante.model.enums.TransactionEvidenceAlgorithm;
import com.restaurante.model.enums.TransactionEvidenceEventStatus;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.model.enums.TransactionEvidenceVerificationStatus;
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
@Table(name = "transaction_evidence_events", indexes = {
        @Index(name = "uq_transaction_evidence_events_seq", columnList = "tenant_id, ledger_sequence", unique = true),
        @Index(name = "uq_transaction_evidence_events_idem", columnList = "tenant_id, idempotency_key", unique = true),
        @Index(name = "idx_transaction_evidence_events_type_time", columnList = "tenant_id, event_type, occurred_at"),
        @Index(name = "idx_transaction_evidence_events_source", columnList = "tenant_id, source_entity_type, source_entity_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TransactionEvidenceEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "ledger_sequence", nullable = false)
    private Long ledgerSequence;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_module", nullable = false, length = 40)
    private TransactionEvidenceSourceModule sourceModule;

    @Column(name = "source_entity_type", nullable = false, length = 80)
    private String sourceEntityType;

    @Column(name = "source_entity_id", nullable = false)
    private Long sourceEntityId;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(name = "canonical_payload_version", nullable = false, length = 40)
    private String canonicalPayloadVersion;

    @Column(name = "canonical_payload_json", nullable = false, columnDefinition = "text")
    private String canonicalPayloadJson;

    @Column(name = "canonical_payload_hash", nullable = false, length = 128)
    private String canonicalPayloadHash;

    @Column(name = "previous_event_hash", nullable = false, length = 128)
    private String previousEventHash;

    @Column(name = "event_hash", nullable = false, length = 128)
    private String eventHash;

    @Column(name = "hmac_signature", nullable = false, length = 256)
    private String hmacSignature;

    @Column(name = "key_version", nullable = false, length = 40)
    private String keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 40)
    private TransactionEvidenceAlgorithm algorithm = TransactionEvidenceAlgorithm.SHA256_HMAC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionEvidenceEventStatus status = TransactionEvidenceEventStatus.RECORDED;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 40)
    private TransactionEvidenceVerificationStatus verificationStatus = TransactionEvidenceVerificationStatus.NOT_VERIFIED;

    @Column(name = "payload_summary_json", columnDefinition = "text")
    private String payloadSummaryJson;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}

