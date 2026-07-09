package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantFiscalCorrectionPolicyStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_fiscal_correction_policies", indexes = {
        @Index(name = "uq_tenant_fiscal_correction_policy_one_active", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantFiscalCorrectionPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantFiscalCorrectionPolicyStatus status = TenantFiscalCorrectionPolicyStatus.ACTIVE;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @NotNull
    @Column(name = "auto_assess_adjustments", nullable = false)
    private boolean autoAssessAdjustments = true;

    @NotNull
    @Column(name = "auto_issue_credit_note", nullable = false)
    private boolean autoIssueCreditNote = false;

    @NotNull
    @Column(name = "auto_issue_debit_note", nullable = false)
    private boolean autoIssueDebitNote = false;

    @NotNull
    @Column(name = "require_manual_review_for_loss", nullable = false)
    private boolean requireManualReviewForLoss = true;

    @NotNull
    @Column(name = "require_manual_review_for_surplus", nullable = false)
    private boolean requireManualReviewForSurplus = true;

    @NotNull
    @Column(name = "allow_correction_without_original_document", nullable = false)
    private boolean allowCorrectionWithoutOriginalDocument = false;

    @NotNull
    @Column(name = "max_auto_correction_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAutoCorrectionAmount = BigDecimal.ZERO;
}

