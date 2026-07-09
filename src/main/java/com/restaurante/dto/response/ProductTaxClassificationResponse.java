package com.restaurante.dto.response;

import com.restaurante.model.enums.ProductTaxClassificationStatus;
import com.restaurante.model.enums.TaxCategory;

import java.time.LocalDateTime;

public class ProductTaxClassificationResponse {
    private Long id;
    private Long tenantId;
    private Long productId;
    private Long taxRateId;
    private TaxCategory taxCategory;
    private String exemptReason;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private ProductTaxClassificationStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
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

