ALTER TABLE business_provisioning_operations
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP,
    ADD COLUMN lease_owner VARCHAR(100),
    ADD COLUMN lease_until TIMESTAMP,
    ADD COLUMN next_retry_at TIMESTAMP,
    ADD COLUMN effects_committed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN command_payload_json TEXT;

CREATE INDEX idx_business_operation_recovery
    ON business_provisioning_operations(status, lease_until, next_retry_at);

ALTER TABLE onboarding_requests
    ADD COLUMN approval_idempotency_key VARCHAR(100),
    ADD COLUMN approval_fingerprint VARCHAR(64);

-- O registo cobre apenas contas criadas pelo contrato canónico. Não impõe
-- qualquer constraint sobre NIFs legados e não altera dados comerciais.
CREATE TABLE canonical_business_account_nifs (
    normalized_nif VARCHAR(30) PRIMARY KEY,
    business_account_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_canonical_nif_account
        FOREIGN KEY (business_account_id) REFERENCES business_accounts(id)
);
