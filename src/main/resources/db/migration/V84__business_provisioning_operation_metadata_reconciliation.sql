-- Reconcilia exclusivamente metadados técnicos de operações criadas antes da V83.
-- Não altera identificadores, payload/resultados, tenant nem dados comerciais.
UPDATE business_provisioning_operations
SET effects_committed = (status = 'SUCCEEDED'),
    attempt_count = CASE
        WHEN status IN ('RUNNING', 'SUCCEEDED', 'FAILED_FINAL', 'FAILED_RETRYABLE')
            THEN GREATEST(attempt_count, 1)
        ELSE attempt_count
    END
WHERE status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED_FINAL', 'FAILED_RETRYABLE');
