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

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_consumption_lines", indexes = {
        @Index(name = "idx_inv_consumption_line_record", columnList = "consumption_record_id"),
        @Index(name = "idx_inv_consumption_line_item", columnList = "tenant_id, inventory_item_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryConsumptionLine extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumption_record_id", nullable = false)
    private InventoryConsumptionRecord consumptionRecord;

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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private InventoryRecipe recipe;

    @NotNull
    @Column(name = "quantity_consumed", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityConsumed;

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
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "stock_before", precision = 19, scale = 6)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", precision = 19, scale = 6)
    private BigDecimal stockAfter;

    @Column(name = "warning_code", length = 120)
    private String warningCode;
}

