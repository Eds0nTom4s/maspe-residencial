package com.restaurante.dto.request;

import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.MarginCalculationBasis;
import com.restaurante.model.enums.TenantInventoryPolicyStatus;
import lombok.Data;

@Data
public class UpsertTenantInventoryPolicyRequest {
    private Boolean stockControlEnabled;
    private Boolean allowNegativeStockDefault;
    private Boolean strictStockRequiredForSale;
    private InventoryConsumptionTriggerType consumptionTrigger;
    private Boolean requireRecipeForStockedProducts;
    private Boolean useAverageCost;
    private MarginCalculationBasis marginCalculationBasis;
    private TenantInventoryPolicyStatus status;

    // Prompt 44.1: devoluções/estornos
    private Boolean allowReturns;
    private Boolean requireReturnApproval;
    private InventoryRestockPolicy defaultRestockPolicy;
    private Boolean allowPartialReturns;
    private Boolean allowReturnAfterTurnoClosed;
    private Integer maxReturnDays;
    private Boolean requireFiscalCreditNoteForReturn;
    private Boolean autoProcessReturnOnCreditNote;

    // Prompt 44.2
    private Boolean autoCreateReturnOnCreditNote;
    private Boolean autoCreateReturnOnRefund;
    private Boolean autoProcessReturnOnRefund;
    private InventoryRestockPolicy defaultRefundRestockPolicy;
    private Boolean requireCreditNoteForFinancialReturn;
    private Boolean blockProcessWhenManualReviewLineExists;
}
