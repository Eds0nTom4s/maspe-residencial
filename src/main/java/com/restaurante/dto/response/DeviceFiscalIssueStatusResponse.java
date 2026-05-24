package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import lombok.Data;

@Data
public class DeviceFiscalIssueStatusResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private Long fiscalDocumentId;
    private FiscalAutoIssueJobStatus jobStatus;
    private String lastErrorCode;
}

