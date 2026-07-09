package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryMovementDirection;
import com.restaurante.model.enums.InventoryMovementReferenceType;
import com.restaurante.model.enums.InventoryMovementSource;
import com.restaurante.model.enums.InventoryMovementType;
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
@Table(name = "inventory_movements", indexes = {
        @Index(name = "idx_inv_movement_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_inv_movement_tenant_item", columnList = "tenant_id, inventory_item_id"),
        @Index(name = "idx_inv_movement_tenant_type", columnList = "tenant_id, movement_type"),
        @Index(name = "idx_inv_movement_tenant_ref", columnList = "tenant_id, reference_type, reference_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InventoryMovement extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 60)
    private InventoryMovementType movementType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private InventoryMovementDirection direction;

    @NotNull
    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

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

    @Column(name = "average_cost_before", precision = 19, scale = 6)
    private BigDecimal averageCostBefore;

    @Column(name = "average_cost_after", precision = 19, scale = 6)
    private BigDecimal averageCostAfter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 60)
    private InventoryMovementReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private InventoryMovementSource source;

    @Column(name = "reason")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_device_id")
    private DispositivoOperacional operationalDevice;
}

