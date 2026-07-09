package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalDocumentSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FiscalDocumentResponse {
    private Long id;
    private Long tenantId;
    private Long unidadeAtendimentoId;
    private Long turnoOperacionalId;
    private Long pedidoId;
    private Long pagamentoId;
    private Long caixaOperadorSessionId;

    private FiscalDocumentType documentType;
    private FiscalDocumentStatus status;
    private FiscalRegime fiscalRegime;
    private String documentNumber;
    private String series;
    private LocalDateTime issuedAt;

    private BigDecimal subtotalAmount;
    private BigDecimal taxableAmount;
    private BigDecimal exemptAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;

    private FiscalDocumentSource source;
    private Long createdByUserId;
    private Long operationalDeviceId;

    private List<FiscalDocumentLineResponse> lines;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }
    public Long getTurnoOperacionalId() { return turnoOperacionalId; }
    public void setTurnoOperacionalId(Long turnoOperacionalId) { this.turnoOperacionalId = turnoOperacionalId; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }
    public Long getCaixaOperadorSessionId() { return caixaOperadorSessionId; }
    public void setCaixaOperadorSessionId(Long caixaOperadorSessionId) { this.caixaOperadorSessionId = caixaOperadorSessionId; }
    public FiscalDocumentType getDocumentType() { return documentType; }
    public void setDocumentType(FiscalDocumentType documentType) { this.documentType = documentType; }
    public FiscalDocumentStatus getStatus() { return status; }
    public void setStatus(FiscalDocumentStatus status) { this.status = status; }
    public FiscalRegime getFiscalRegime() { return fiscalRegime; }
    public void setFiscalRegime(FiscalRegime fiscalRegime) { this.fiscalRegime = fiscalRegime; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
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
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public FiscalDocumentSource getSource() { return source; }
    public void setSource(FiscalDocumentSource source) { this.source = source; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public Long getOperationalDeviceId() { return operationalDeviceId; }
    public void setOperationalDeviceId(Long operationalDeviceId) { this.operationalDeviceId = operationalDeviceId; }
    public List<FiscalDocumentLineResponse> getLines() { return lines; }
    public void setLines(List<FiscalDocumentLineResponse> lines) { this.lines = lines; }

    public static class FiscalDocumentLineResponse {
        private Long id;
        private Long productId;
        private Long pedidoItemId;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal netAmount;
        private String taxRateCode;
        private BigDecimal taxRateValue;
        private BigDecimal taxAmount;
        private BigDecimal grossAmount;
        private String taxCategory;
        private String exemptReason;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Long getPedidoItemId() { return pedidoItemId; }
        public void setPedidoItemId(Long pedidoItemId) { this.pedidoItemId = pedidoItemId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getNetAmount() { return netAmount; }
        public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
        public String getTaxRateCode() { return taxRateCode; }
        public void setTaxRateCode(String taxRateCode) { this.taxRateCode = taxRateCode; }
        public BigDecimal getTaxRateValue() { return taxRateValue; }
        public void setTaxRateValue(BigDecimal taxRateValue) { this.taxRateValue = taxRateValue; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
        public BigDecimal getGrossAmount() { return grossAmount; }
        public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
        public String getTaxCategory() { return taxCategory; }
        public void setTaxCategory(String taxCategory) { this.taxCategory = taxCategory; }
        public String getExemptReason() { return exemptReason; }
        public void setExemptReason(String exemptReason) { this.exemptReason = exemptReason; }
    }
}

