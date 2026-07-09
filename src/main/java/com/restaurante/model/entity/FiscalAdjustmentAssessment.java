package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import com.restaurante.model.enums.FiscalAdjustmentImpactType;
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
@Table(name = "fiscal_adjustment_assessments", indexes = {
        @Index(name = "idx_fiscal_assessment_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_fiscal_assessment_tenant_created_at", columnList = "tenant_id, created_at"),
        @Index(name = "uq_fiscal_assessment_one_per_adjustment", columnList = "tenant_id, caixa_operador_adjustment_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FiscalAdjustmentAssessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_operador_adjustment_id", nullable = false)
    private CaixaOperadorAdjustment adjustment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_operador_divergence_id")
    private CaixaOperadorDivergence divergence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_operador_session_id")
    private CaixaOperadorSession caixaOperadorSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_operacional_id")
    private TurnoOperacional turnoOperacional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_fiscal_document_id")
    private FiscalDocument originalFiscalDocument;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 60)
    private FiscalAdjustmentAssessmentStatus status = FiscalAdjustmentAssessmentStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 60)
    private FiscalAdjustmentImpactType impactType = FiscalAdjustmentImpactType.MANUAL_REVIEW_REQUIRED;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by_user_id")
    private User assessedBy;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;
}

