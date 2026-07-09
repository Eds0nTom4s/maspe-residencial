package com.restaurante.dto.request;

import com.restaurante.model.enums.ProductTaxClassificationStatus;
import com.restaurante.model.enums.TaxCategory;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class UpsertProductTaxClassificationRequest {
    @NotNull
    private Long productId;
    private Long taxRateId;
    @NotNull
    private TaxCategory taxCategory = TaxCategory.STANDARD;
    private String exemptReason;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @NotNull
    private ProductTaxClassificationStatus status = ProductTaxClassificationStatus.ACTIVE;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getTaxRateId() { return taxRateId; }
    public void setTaxRateId(Long taxRateId) { this.taxRateId = taxRateId; }
    public TaxCategory getTaxCategory() { return taxCategory; }
    public void setTaxCategory(TaxCategory taxCategory) { this.taxCategory = taxCategory; }
    public String getExemptReason() { return exemptReason; }
    public void setExemptReason(String exemptReason) { this.exemptReason = exemptReason; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public ProductTaxClassificationStatus getStatus() { return status; }
    public void setStatus(ProductTaxClassificationStatus status) { this.status = status; }
}

