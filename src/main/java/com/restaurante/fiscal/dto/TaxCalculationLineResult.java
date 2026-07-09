package com.restaurante.fiscal.dto;

import com.restaurante.model.enums.TaxCategory;

import java.math.BigDecimal;

public class TaxCalculationLineResult {
    private Long pedidoItemId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal netAmount;
    private Long taxRateId;
    private String taxRateCode;
    private BigDecimal taxRateValue;
    private BigDecimal taxAmount;
    private BigDecimal grossAmount;
    private TaxCategory taxCategory;
    private String exemptReason;

    public Long getPedidoItemId() { return pedidoItemId; }
    public void setPedidoItemId(Long pedidoItemId) { this.pedidoItemId = pedidoItemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public Long getTaxRateId() { return taxRateId; }
    public void setTaxRateId(Long taxRateId) { this.taxRateId = taxRateId; }
    public String getTaxRateCode() { return taxRateCode; }
    public void setTaxRateCode(String taxRateCode) { this.taxRateCode = taxRateCode; }
    public BigDecimal getTaxRateValue() { return taxRateValue; }
    public void setTaxRateValue(BigDecimal taxRateValue) { this.taxRateValue = taxRateValue; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public TaxCategory getTaxCategory() { return taxCategory; }
    public void setTaxCategory(TaxCategory taxCategory) { this.taxCategory = taxCategory; }
    public String getExemptReason() { return exemptReason; }
    public void setExemptReason(String exemptReason) { this.exemptReason = exemptReason; }
}
