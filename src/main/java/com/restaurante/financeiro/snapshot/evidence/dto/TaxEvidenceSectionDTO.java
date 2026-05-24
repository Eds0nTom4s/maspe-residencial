package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaxEvidenceSectionDTO {
    private LocalDateTime generatedAt;
    private Long tenantId;
    private Long turnoId;
    private String fiscalRegime;

    private Integer totalDocuments;
    private Integer issuedDocuments;
    private Integer cancelledDocuments;

    // Prompt 43.1: auto-emissão fiscal pós-pagamento (saúde operacional)
    private Boolean autoIssueEnabled;
    private Boolean autoIssueOnPayment;
    private Integer totalAutoIssueJobs;
    private Integer pendingAutoIssueJobs;
    private Integer failedRetryableAutoIssueJobs;
    private Integer failedPermanentAutoIssueJobs;
    private Integer skippedAutoIssueJobs;
    private Integer issuedByAutoIssueJobs;
    private Integer confirmedPaymentsWithoutFiscalDocument;

    private BigDecimal taxableAmount;
    private BigDecimal exemptAmount;
    private BigDecimal taxAmount;
    private BigDecimal grossAmount;

    private List<TaxEvidenceByTaxRateDTO> byTaxRate;
    private List<String> warnings;
    private List<TaxEvidenceDocumentItemDTO> documents;
}
