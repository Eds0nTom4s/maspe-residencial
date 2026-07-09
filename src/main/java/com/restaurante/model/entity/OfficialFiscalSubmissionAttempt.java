package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "official_fiscal_submission_attempts", indexes = {
        @Index(name = "idx_ofsa_tenant_submission", columnList = "tenant_id, official_fiscal_submission_id"),
        @Index(name = "idx_ofsa_tenant_request", columnList = "tenant_id, request_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OfficialFiscalSubmissionAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "official_fiscal_submission_id", nullable = false)
    private OfficialFiscalSubmission submission;

    @NotNull
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @NotNull
    @Column(name = "status", nullable = false, length = 60)
    private String status;

    @Column(name = "request_id", length = 160)
    private String requestId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_payload_hash", length = 64)
    private String requestPayloadHash;

    @Column(name = "response_payload_hash", length = 64)
    private String responsePayloadHash;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}

