package com.restaurante.txevidence.key;

import java.util.Optional;

public interface TransactionEvidenceKeyProvider {
    String activeKeyVersion();
    Optional<String> hmacSecretForVersion(String keyVersion);
}

