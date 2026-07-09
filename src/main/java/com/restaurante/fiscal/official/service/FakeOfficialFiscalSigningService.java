package com.restaurante.fiscal.official.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FakeOfficialFiscalSigningService implements OfficialFiscalSigningService {

    private final OfficialFiscalPayloadCanonicalService canonicalService;

    @Override
    public SignedPayload signCanonicalPayload(String canonicalPayload) {
        String payloadHash = canonicalService.sha256Hex(canonicalPayload);
        String jwsHash = canonicalService.sha256Hex("FAKE-JWS-RS256:" + payloadHash);
        // "signedPayloadHash" representa o hash do "conteúdo assinado" (placeholder, sem JWS real)
        String signedPayloadHash = canonicalService.sha256Hex("SIGNED:" + payloadHash + ":" + jwsHash);
        return new SignedPayload(signedPayloadHash, jwsHash);
    }
}

