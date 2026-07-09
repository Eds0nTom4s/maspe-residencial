package com.restaurante.txevidence.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consuma.evidence.tx-ledger")
public class TransactionEvidenceProperties {
    private boolean enabled = true;
    private String keyVersion = "1";
    private String canonicalPayloadVersion = "tx-evidence-v1";
    /**
     * Dev/test-only fallback secret. Never set in production.
     * When present, it is used if the environment variable key is missing.
     */
    private String devHmacSecret;
}
