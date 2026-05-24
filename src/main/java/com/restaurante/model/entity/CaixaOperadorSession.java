package com.restaurante.model.entity;

import com.restaurante.model.enums.CaixaOperadorSessionStatus;
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
@Table(name = "caixa_operador_sessions", indexes = {
        @Index(name = "idx_caixa_operador_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_caixa_operador_tenant_turno", columnList = "tenant_id, turno_operacional_id"),
        @Index(name = "idx_caixa_operador_tenant_device_status", columnList = "tenant_id, operational_device_id, status"),
        @Index(name = "idx_caixa_operador_tenant_operador_status", columnList = "tenant_id, operador_user_id, status"),
        @Index(name = "idx_caixa_operador_opened_at", columnList = "tenant_id, opened_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CaixaOperadorSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_operacional_id")
    private TurnoOperacional turnoOperacional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operational_device_id", nullable = false)
    private DispositivoOperacional dispositivoOperacional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operador_user_id", nullable = false)
    private User operador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_user_id", nullable = false)
    private User openedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CaixaOperadorSessionStatus status = CaixaOperadorSessionStatus.OPEN;

    @NotNull
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @NotNull
    @Column(name = "expected_cash_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedCashAmount = BigDecimal.ZERO;

    @Column(name = "declared_cash_amount", precision = 19, scale = 2)
    private BigDecimal declaredCashAmount;

    @Column(name = "cash_difference_amount", precision = 19, scale = 2)
    private BigDecimal cashDifferenceAmount;

    @NotNull
    @Column(name = "expected_tpa_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedTpaAmount = BigDecimal.ZERO;

    @Column(name = "declared_tpa_amount", precision = 19, scale = 2)
    private BigDecimal declaredTpaAmount;

    @Column(name = "tpa_difference_amount", precision = 19, scale = 2)
    private BigDecimal tpaDifferenceAmount;

    @NotNull
    @Column(name = "expected_manual_total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedManualTotalAmount = BigDecimal.ZERO;

    @Column(name = "declared_manual_total_amount", precision = 19, scale = 2)
    private BigDecimal declaredManualTotalAmount;

    @Column(name = "manual_difference_amount", precision = 19, scale = 2)
    private BigDecimal manualDifferenceAmount;

    @NotNull
    @Column(name = "expected_appypay_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAppyPayAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Column(name = "notes")
    private String notes;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "review_notes")
    private String reviewNotes;
}
