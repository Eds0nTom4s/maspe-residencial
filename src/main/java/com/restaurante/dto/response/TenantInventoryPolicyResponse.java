package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import com.restaurante.model.enums.MarginCalculationBasis;
import com.restaurante.model.enums.TenantInventoryPolicyStatus;
import lombok.Data;

@Data
public class TenantInventoryPolicyResponse {
    private Long id;
    private Boolean stockControlEnabled;
    private Boolean allowNegativeStockDefault;
    private Boolean strictStockRequiredForSale;
    private InventoryConsumptionTriggerType consumptionTrigger;
    private Boolean requireRecipeForStockedProducts;
    private Boolean useAverageCost;
    private MarginCalculationBasis marginCalculationBasis;
    private TenantInventoryPolicyStatus status;
}

