package com.restaurante.fiscal.official.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OfficialFiscalDocumentPayload {
    private String authority;
    private String countryCode;
    private String taxpayerNumber;
    private String softwareCertificateId;

    private String documentType;
    private String documentNumber;
    private String series;
    private LocalDateTime issuedAt;
    private String fiscalRegime;
    private String currency;

    private OfficialFiscalCustomerDTO customer;
    private OfficialFiscalTotalsDTO totals;
    private List<OfficialFiscalLineDTO> lines;
    private OfficialFiscalCorrectionInfoDTO correctionInfo;
    private String sourceVersion;

    @Data
    public static class OfficialFiscalCustomerDTO {
        private String name;
        private String taxpayerNumber;
        private String countryCode;
    }

    @Data
    public static class OfficialFiscalTotalsDTO {
        private BigDecimal taxableAmount;
        private BigDecimal exemptAmount;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
    }

    @Data
    public static class OfficialFiscalLineDTO {
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
    }

    @Data
    public static class OfficialFiscalCorrectionInfoDTO {
        private String originalDocumentNumber;
        private Long originalDocumentId;
        private String correctionReason;
        private String correctionType;
    }
}

