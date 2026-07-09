package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryConsumptionStatus;
import com.restaurante.model.enums.InventoryConsumptionTriggerType;
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
@Table(name = "inventory_consumption_records", indexes = {
        @Index(name = "idx_inv_consumption_record_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_inv_consumption_record_tenant_created", columnList = "tenant_id, created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryConsumptionRecord extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InventoryConsumptionStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 60)
    private InventoryConsumptionTriggerType triggerType;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "gross_revenue_amount", precision = 19, scale = 2)
    private BigDecimal grossRevenueAmount;

    @Column(name = "net_revenue_amount", precision = 19, scale = 2)
    private BigDecimal netRevenueAmount;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_cost", precision = 19, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "estimated_margin_amount", precision = 19, scale = 2)
    private BigDecimal estimatedMarginAmount;

    @Column(name = "estimated_margin_percentage", precision = 19, scale = 6)
    private BigDecimal estimatedMarginPercentage;

    @NotNull
    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;
}
