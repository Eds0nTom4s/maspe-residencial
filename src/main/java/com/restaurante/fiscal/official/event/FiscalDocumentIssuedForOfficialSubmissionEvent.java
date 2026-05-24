package com.restaurante.fiscal.official.event;

import com.restaurante.model.enums.FiscalDocumentType;

import java.time.LocalDateTime;

public record FiscalDocumentIssuedForOfficialSubmissionEvent(
        Long tenantId,
        Long fiscalDocumentId,
        FiscalDocumentType documentType,
        LocalDateTime issuedAt
) {}

