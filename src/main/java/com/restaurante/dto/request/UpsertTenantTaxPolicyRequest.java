package com.restaurante.dto.request;

import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantTaxPolicyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class UpsertTenantTaxPolicyRequest {
    @NotBlank
    private String name;
    @NotNull
    private FiscalRegime fiscalRegime;
    private Long defaultTaxRateId;
    private boolean pricesIncludeTax;
    private boolean allowTaxExemptItems = true;
    private boolean requireTaxDocumentOnPayment = false;
    @NotNull
    private TenantTaxPolicyStatus status = TenantTaxPolicyStatus.ACTIVE;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FiscalRegime getFiscalRegime() { return fiscalRegime; }
    public void setFiscalRegime(FiscalRegime fiscalRegime) { this.fiscalRegime = fiscalRegime; }
    public Long getDefaultTaxRateId() { return defaultTaxRateId; }
    public void setDefaultTaxRateId(Long defaultTaxRateId) { this.defaultTaxRateId = defaultTaxRateId; }
    public boolean isPricesIncludeTax() { return pricesIncludeTax; }
    public void setPricesIncludeTax(boolean pricesIncludeTax) { this.pricesIncludeTax = pricesIncludeTax; }
    public boolean isAllowTaxExemptItems() { return allowTaxExemptItems; }
    public void setAllowTaxExemptItems(boolean allowTaxExemptItems) { this.allowTaxExemptItems = allowTaxExemptItems; }
    public boolean isRequireTaxDocumentOnPayment() { return requireTaxDocumentOnPayment; }
    public void setRequireTaxDocumentOnPayment(boolean requireTaxDocumentOnPayment) { this.requireTaxDocumentOnPayment = requireTaxDocumentOnPayment; }
    public TenantTaxPolicyStatus getStatus() { return status; }
    public void setStatus(TenantTaxPolicyStatus status) { this.status = status; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
}

