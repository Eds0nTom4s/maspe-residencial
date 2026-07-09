package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleVerificationResponse {
    private boolean valido;
    private Boolean bundleHashValido;
    private Boolean bundleSignatureValida;
    private Boolean chainHashValido;
    private Boolean chainSignatureValida;
    private Boolean previousLinkValido;

    private String failureReason;
    private LocalDateTime verificadoEm;

    private String bundleHashPersistido;
    private String bundleHashRecalculado;

    private Boolean signatureKeyFound;
    private String signatureKeyStatus;
    private String signatureFailureReason;

    private Boolean chainSignatureKeyFound;
    private String chainSignatureKeyStatus;
    private String chainSignatureFailureReason;
}

