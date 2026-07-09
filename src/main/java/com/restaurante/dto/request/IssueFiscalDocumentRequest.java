package com.restaurante.dto.request;

import com.restaurante.model.enums.FiscalDocumentType;
import jakarta.validation.constraints.NotNull;

public class IssueFiscalDocumentRequest {
    @NotNull
    private FiscalDocumentType documentType = FiscalDocumentType.INTERNAL_RECEIPT;

    private String series = "A";
    private String customerName;
    private String customerTaxpayerNumber;

    public FiscalDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(FiscalDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerTaxpayerNumber() {
        return customerTaxpayerNumber;
    }

    public void setCustomerTaxpayerNumber(String customerTaxpayerNumber) {
        this.customerTaxpayerNumber = customerTaxpayerNumber;
    }
}

