package com.restaurante.fiscal.dto;

import com.restaurante.model.enums.FiscalRegime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TaxCalculationResult {
    private Long tenantId;
    private Long pedidoId;
    private FiscalRegime fiscalRegime;
    private boolean pricesIncludeTax;

    private BigDecimal subtotalAmount = BigDecimal.ZERO;
    private BigDecimal taxableAmount = BigDecimal.ZERO;
    private BigDecimal exemptAmount = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private final List<TaxCalculationLineResult> lines = new ArrayList<>();

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public FiscalRegime getFiscalRegime() { return fiscalRegime; }
    public void setFiscalRegime(FiscalRegime fiscalRegime) { this.fiscalRegime = fiscalRegime; }
    public boolean isPricesIncludeTax() { return pricesIncludeTax; }
    public void setPricesIncludeTax(boolean pricesIncludeTax) { this.pricesIncludeTax = pricesIncludeTax; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getExemptAmount() { return exemptAmount; }
    public void setExemptAmount(BigDecimal exemptAmount) { this.exemptAmount = exemptAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public List<TaxCalculationLineResult> getLines() { return lines; }
}

