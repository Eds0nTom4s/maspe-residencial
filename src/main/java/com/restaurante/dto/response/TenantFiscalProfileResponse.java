package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantFiscalProfileStatus;

public class TenantFiscalProfileResponse {
    private Long id;
    private Long tenantId;
    private TenantFiscalProfileStatus status;
    private FiscalRegime fiscalRegime;
    private String taxpayerNumber;
    private String legalName;
    private String commercialName;
    private String countryCode;
    private String province;
    private String municipality;
    private String address;
    private Long defaultTaxPolicyId;
    private boolean invoiceRequired;
    private boolean fiscalDocumentEnabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public TenantFiscalProfileStatus getStatus() { return status; }
    public void setStatus(TenantFiscalProfileStatus status) { this.status = status; }
    public FiscalRegime getFiscalRegime() { return fiscalRegime; }
    public void setFiscalRegime(FiscalRegime fiscalRegime) { this.fiscalRegime = fiscalRegime; }
    public String getTaxpayerNumber() { return taxpayerNumber; }
    public void setTaxpayerNumber(String taxpayerNumber) { this.taxpayerNumber = taxpayerNumber; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getCommercialName() { return commercialName; }
    public void setCommercialName(String commercialName) { this.commercialName = commercialName; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getDefaultTaxPolicyId() { return defaultTaxPolicyId; }
    public void setDefaultTaxPolicyId(Long defaultTaxPolicyId) { this.defaultTaxPolicyId = defaultTaxPolicyId; }
    public boolean isInvoiceRequired() { return invoiceRequired; }
    public void setInvoiceRequired(boolean invoiceRequired) { this.invoiceRequired = invoiceRequired; }
    public boolean isFiscalDocumentEnabled() { return fiscalDocumentEnabled; }
    public void setFiscalDocumentEnabled(boolean fiscalDocumentEnabled) { this.fiscalDocumentEnabled = fiscalDocumentEnabled; }
}

