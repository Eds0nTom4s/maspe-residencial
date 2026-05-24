package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalAuthority;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.OfficialFiscalEnvironment;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "official_fiscal_submissions", indexes = {
        @Index(name = "idx_ofs_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_ofs_status_next_attempt", columnList = "status, next_attempt_at"),
        @Index(name = "idx_ofs_request_id", columnList = "tenant_id, request_id"),
        @Index(name = "idx_ofs_locked_at", columnList = "status, locked_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OfficialFiscalSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiscal_document_id", nullable = false)
    private FiscalDocument fiscalDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_fiscal_document_id")
    private FiscalDocument originalFiscalDocument;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 80)
    private FiscalDocumentType documentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 60)
    private OfficialFiscalSubmissionStatus status = OfficialFiscalSubmissionStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "authority", nullable = false, length = 40)
    private FiscalAuthority authority = FiscalAuthority.AGT_AO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 40)
    private OfficialFiscalEnvironment environment = OfficialFiscalEnvironment.SANDBOX;

    @Column(name = "request_id", length = 160)
    private String requestId;

    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "official_document_id", length = 160)
    private String officialDocumentId;

    @Column(name = "official_status_code", length = 80)
    private String officialStatusCode;

    @Column(name = "official_status_message")
    private String officialStatusMessage;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "signed_payload_hash", length = 64)
    private String signedPayloadHash;

    @Column(name = "jws_document_signature_hash", length = 64)
    private String jwsDocumentSignatureHash;

    @Column(name = "jws_request_signature_hash", length = 64)
    private String jwsRequestSignatureHash;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @NotNull
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;
}

