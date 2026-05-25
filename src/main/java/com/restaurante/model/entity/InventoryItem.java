package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryItemStatus;
import com.restaurante.model.enums.InventoryItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items", indexes = {
        @Index(name = "idx_inventory_item_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_inventory_item_tenant_type", columnList = "tenant_id, type"),
        @Index(name = "idx_inventory_item_tenant_sku", columnList = "tenant_id, sku")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryItem extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "sku", length = 80)
    private String sku;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    private InventoryItemType type;

    @Column(name = "category", length = 120)
    private String category;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_unit_id", nullable = false)
    private UnitOfMeasure baseUnit;

    @NotNull
    @Column(name = "stock_control_enabled", nullable = false)
    private Boolean stockControlEnabled = Boolean.TRUE;

    @NotNull
    @Column(name = "allow_negative_stock", nullable = false)
    private Boolean allowNegativeStock = Boolean.TRUE;

    @NotNull
    @Column(name = "current_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @NotNull
    @Column(name = "average_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @NotNull
    @Column(name = "last_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal lastCost = BigDecimal.ZERO;

    @Column(name = "minimum_quantity", precision = 19, scale = 6)
    private BigDecimal minimumQuantity;

    @Column(name = "reorder_quantity", precision = 19, scale = 6)
    private BigDecimal reorderQuantity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InventoryItemStatus status = InventoryItemStatus.ACTIVE;
}

