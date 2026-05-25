package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryRestockPolicy;
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

@Entity
@Table(name = "inventory_return_lines", indexes = {
        @Index(name = "idx_inv_return_lines_record", columnList = "inventory_return_record_id"),
        @Index(name = "idx_inv_return_lines_tenant_item", columnList = "tenant_id, inventory_item_id"),
        @Index(name = "idx_inv_return_lines_tenant_pedido_item", columnList = "tenant_id, pedido_item_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryReturnLine extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_return_record_id", nullable = false)
    private InventoryReturnRecord returnRecord;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_id")
    private ItemPedido pedidoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Produto product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_consumption_line_id")
    private InventoryConsumptionLine consumptionLine;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private InventoryRecipe recipe;

    @NotNull
    @Column(name = "quantity_returned", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityReturned;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    @NotNull
    @Column(name = "quantity_base_unit", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityBaseUnit;

    @NotNull
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_cost_reversed", nullable = false, precision = 19, scale = 6)
    private BigDecimal totalCostReversed = BigDecimal.ZERO;

    @Column(name = "stock_before", precision = 19, scale = 6)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", precision = 19, scale = 6)
    private BigDecimal stockAfter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "restock_policy", nullable = false, length = 40)
    private InventoryRestockPolicy restockPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id")
    private InventoryMovement movement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waste_movement_id")
    private InventoryMovement wasteMovement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cogs_reversal_movement_id")
    private InventoryMovement cogsReversalMovement;

    @Column(name = "total_revenue_reversed", precision = 19, scale = 2)
    private BigDecimal totalRevenueReversed;

    @Column(name = "total_tax_reversed", precision = 19, scale = 2)
    private BigDecimal totalTaxReversed;

    @Column(name = "total_margin_reversed", precision = 19, scale = 2)
    private BigDecimal totalMarginReversed;

    @Column(name = "warning_code", length = 120)
    private String warningCode;
}
