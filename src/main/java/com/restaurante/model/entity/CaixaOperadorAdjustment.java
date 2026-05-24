package com.restaurante.model.entity;

import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;
import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
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
@Table(name = "caixa_operador_adjustments", indexes = {
        @Index(name = "idx_caixa_adj_tenant", columnList = "tenant_id"),
        @Index(name = "idx_caixa_adj_div", columnList = "tenant_id, caixa_operador_divergence_id"),
        @Index(name = "idx_caixa_adj_caixa", columnList = "tenant_id, caixa_operador_session_id"),
        @Index(name = "idx_caixa_adj_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CaixaOperadorAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_operador_divergence_id", nullable = false)
    private CaixaOperadorDivergence divergence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_operador_session_id", nullable = false)
    private CaixaOperadorSession caixaOperadorSession;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 80)
    private CaixaOperadorAdjustmentType adjustmentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private CaixaOperadorDivergencePaymentMethod paymentMethod;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 60)
    private CaixaOperadorAdjustmentDirection direction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CaixaOperadorAdjustmentStatus status = CaixaOperadorAdjustmentStatus.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reason")
    private String reason;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;
}

