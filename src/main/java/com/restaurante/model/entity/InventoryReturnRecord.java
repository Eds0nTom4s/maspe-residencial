package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnStatus;
import com.restaurante.model.enums.InventoryReturnType;
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
@Table(name = "inventory_return_records", indexes = {
        @Index(name = "idx_inv_return_record_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_inv_return_record_tenant_pedido", columnList = "tenant_id, pedido_id"),
        @Index(name = "idx_inv_return_record_tenant_processed", columnList = "tenant_id, processed_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryReturnRecord extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id")
    private FiscalDocument fiscalDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_correction_document_id")
    private FiscalDocument fiscalCorrectionDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_credit_note_id")
    private FiscalDocument fiscalCreditNote;

    @Column(name = "refund_reference_id", length = 120)
    private String refundReferenceId;

    @Column(name = "refund_event_id", length = 120)
    private String refundEventId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_consumption_record_id", nullable = false)
    private InventoryConsumptionRecord consumptionRecord;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 60)
    private InventoryReturnType returnType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InventoryReturnStatus status = InventoryReturnStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private InventoryReturnSource source = InventoryReturnSource.ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", length = 80)
    private InventoryReturnReasonCategory reasonCategory;

    @Column(name = "reason_description")
    private String reasonDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "total_return_cost", precision = 19, scale = 2)
    private BigDecimal totalReturnCost;

    @Column(name = "total_revenue_reversed", precision = 19, scale = 2)
    private BigDecimal totalRevenueReversed;

    @Column(name = "total_tax_reversed", precision = 19, scale = 2)
    private BigDecimal totalTaxReversed;

    @Column(name = "total_margin_reversed", precision = 19, scale = 2)
    private BigDecimal totalMarginReversed;

    @NotNull
    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;
}
