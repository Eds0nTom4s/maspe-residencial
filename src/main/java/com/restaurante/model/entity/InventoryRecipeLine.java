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
@Table(name = "inventory_recipe_lines", indexes = {
        @Index(name = "idx_inventory_recipe_lines_recipe", columnList = "recipe_id"),
        @Index(name = "idx_inventory_recipe_lines_item", columnList = "tenant_id, inventory_item_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryRecipeLine extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private InventoryRecipe recipe;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @NotNull
    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    @NotNull
    @Column(name = "waste_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal wastePercentage = BigDecimal.ZERO;

    @Column(name = "cost_snapshot", precision = 19, scale = 6)
    private BigDecimal costSnapshot;
}

