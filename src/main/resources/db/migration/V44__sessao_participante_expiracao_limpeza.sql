-- Prompt 41.3: TTL/expiração/cancelamento/reenvio e limpeza operacional de pendências

ALTER TABLE sessao_consumo_participantes
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS expired_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS cancelled_by_participante_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS cancelled_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS resend_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_resend_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS expiration_reason VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS cleanup_batch_id VARCHAR(120) NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sessao_participante_resend_count_nonneg') THEN
        ALTER TABLE sessao_consumo_participantes
            ADD CONSTRAINT ck_sessao_participante_resend_count_nonneg CHECK (resend_count >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sessao_participante_cancelled_by_participante') THEN
        ALTER TABLE sessao_consumo_participantes
            ADD CONSTRAINT fk_sessao_participante_cancelled_by_participante
                FOREIGN KEY (cancelled_by_participante_id) REFERENCES sessao_consumo_participantes(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sessao_participante_cancelled_by_device') THEN
        ALTER TABLE sessao_consumo_participantes
            ADD CONSTRAINT fk_sessao_participante_cancelled_by_device
                FOREIGN KEY (cancelled_by_device_id) REFERENCES dispositivos_operacionais(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sessao_participantes_expiration
    ON sessao_consumo_participantes (tenant_id, status, expires_at);
