package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantTaxPolicyStatus;

import java.time.LocalDateTime;

public class TenantTaxPolicyResponse {
    private Long id;
    private Long tenantId;
    private String name;
    private FiscalRegime fiscalRegime;
    private Long defaultTaxRateId;
    private boolean pricesIncludeTax;
    private boolean allowTaxExemptItems;
    private boolean requireTaxDocumentOnPayment;
    private TenantTaxPolicyStatus status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
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

