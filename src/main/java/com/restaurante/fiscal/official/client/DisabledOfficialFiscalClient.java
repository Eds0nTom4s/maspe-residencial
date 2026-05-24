package com.restaurante.fiscal.official.client;

import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;
import org.springframework.stereotype.Component;

@Component
public class DisabledOfficialFiscalClient implements OfficialFiscalClient {
    @Override
    public SubmitResult submitDocument(String requestId, String idempotencyKey, OfficialFiscalDocumentPayload payload, String jwsPlaceholderHash) {
        return new SubmitResult(false, null, "CLIENT_DISABLED", "Official fiscal client is disabled (Prompt 45 placeholder).");
    }
}

