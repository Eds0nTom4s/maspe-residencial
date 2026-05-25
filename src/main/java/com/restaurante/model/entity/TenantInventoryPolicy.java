package com.restaurante.model.entity;

import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import com.restaurante.model.enums.MarginCalculationBasis;
import com.restaurante.model.enums.TenantInventoryPolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_inventory_policies")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantInventoryPolicy extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @Column(name = "stock_control_enabled", nullable = false)
    private Boolean stockControlEnabled = Boolean.TRUE;

    @NotNull
    @Column(name = "allow_negative_stock_default", nullable = false)
    private Boolean allowNegativeStockDefault = Boolean.TRUE;

    @NotNull
    @Column(name = "strict_stock_required_for_sale", nullable = false)
    private Boolean strictStockRequiredForSale = Boolean.FALSE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "consumption_trigger", nullable = false, length = 60)
    private InventoryConsumptionTriggerType consumptionTrigger = InventoryConsumptionTriggerType.PAYMENT_CONFIRMED;

    @NotNull
    @Column(name = "require_recipe_for_stocked_products", nullable = false)
    private Boolean requireRecipeForStockedProducts = Boolean.FALSE;

    @NotNull
    @Column(name = "use_average_cost", nullable = false)
    private Boolean useAverageCost = Boolean.TRUE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "margin_calculation_basis", nullable = false, length = 60)
    private MarginCalculationBasis marginCalculationBasis = MarginCalculationBasis.NET_REVENUE_EXCLUDING_TAX;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TenantInventoryPolicyStatus status = TenantInventoryPolicyStatus.ACTIVE;
}

