package com.restaurante.fiscal.official.client;

import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;

public interface OfficialFiscalClient {

    SubmitResult submitDocument(String requestId, String idempotencyKey, OfficialFiscalDocumentPayload payload, String jwsPlaceholderHash);

    record SubmitResult(
            boolean accepted,
            String officialDocumentId,
            String statusCode,
            String statusMessage
    ) {}
}

