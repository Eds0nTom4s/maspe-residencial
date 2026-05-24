package com.restaurante.model.entity;

import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory;
import com.restaurante.model.enums.CaixaOperadorDivergenceSeverity;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
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
@Table(name = "caixa_operador_divergences", indexes = {
        @Index(name = "idx_caixa_div_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_caixa_div_caixa", columnList = "tenant_id, caixa_operador_session_id"),
        @Index(name = "idx_caixa_div_turno", columnList = "tenant_id, turno_operacional_id"),
        @Index(name = "idx_caixa_div_operador", columnList = "tenant_id, operador_user_id"),
        @Index(name = "idx_caixa_div_device", columnList = "tenant_id, operational_device_id"),
        @Index(name = "idx_caixa_div_severity", columnList = "tenant_id, severity"),
        @Index(name = "idx_caixa_div_created_at", columnList = "tenant_id, created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CaixaOperadorDivergence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_operacional_id")
    private TurnoOperacional turnoOperacional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_operador_session_id", nullable = false)
    private CaixaOperadorSession caixaOperadorSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operational_device_id", nullable = false)
    private DispositivoOperacional dispositivoOperacional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operador_user_id", nullable = false)
    private User operador;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CaixaOperadorDivergenceStatus status = CaixaOperadorDivergenceStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    private CaixaOperadorDivergenceType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 40)
    private CaixaOperadorDivergenceSeverity severity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private CaixaOperadorDivergencePaymentMethod paymentMethod;

    @NotNull
    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @NotNull
    @Column(name = "declared_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredAmount;

    @NotNull
    @Column(name = "difference_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal differenceAmount;

    @NotNull
    @Column(name = "absolute_difference_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal absoluteDifferenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", length = 80)
    private CaixaOperadorDivergenceReasonCategory reasonCategory;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id")
    private User submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes")
    private String reviewNotes;
}

