package com.restaurante.model.entity;

import com.restaurante.model.enums.ProductStockPolicy;
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

@Entity
@Table(name = "product_inventory_mappings", indexes = {
        @Index(name = "idx_prod_inv_map_tenant_policy", columnList = "tenant_id, stock_policy")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductInventoryMapping extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private InventoryRecipe recipe;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_policy", nullable = false, length = 60)
    private ProductStockPolicy stockPolicy = ProductStockPolicy.NO_STOCK_CONTROL;

    @NotNull
    @Column(name = "status", nullable = false, length = 40)
    private String status = "ACTIVE";
}

