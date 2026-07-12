ALTER TABLE pagamentos_gateway
    ADD COLUMN reconciliation_last_response_hash VARCHAR(64),
    ADD COLUMN reconciliation_last_remote_status VARCHAR(50),
    ADD COLUMN reconciliation_last_attempt_at TIMESTAMP,
    ADD COLUMN reconciliation_next_attempt_at TIMESTAMP,
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reconciliation_last_error TEXT,
    ADD COLUMN reconciliation_status VARCHAR(50);

CREATE INDEX idx_pagamento_reconciliation_eligible
    ON pagamentos_gateway (status, reconciliation_status, reconciliation_next_attempt_at)
    WHERE status = 'PENDENTE';
