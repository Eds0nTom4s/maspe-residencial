package com.restaurante.fiscal.official.service;

public interface OfficialFiscalSigningService {

    SignedPayload signCanonicalPayload(String canonicalPayload);

    record SignedPayload(
            String signedPayloadHash,
            String jwsPlaceholderHash
    ) {}
}

