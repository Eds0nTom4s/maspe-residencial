-- Prompt 41.2: política de entrada da sessão compartilhada + aprovação/convite por OWNER/POS

ALTER TABLE sessoes_consumo
    ADD COLUMN IF NOT EXISTS participant_entry_policy VARCHAR(50) NOT NULL DEFAULT 'OTP_AUTO_JOIN',
    ADD COLUMN IF NOT EXISTS participant_policy_updated_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS participant_policy_updated_by_cliente_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS participant_policy_updated_by_device_id BIGINT NULL;

ALTER TABLE sessao_consumo_participantes
    ADD COLUMN IF NOT EXISTS invited_by_participante_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS invited_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS invited_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS invitation_expires_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS approval_requested_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS approved_by_participante_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS approved_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS rejected_by_participante_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS rejected_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS approval_decided_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS approval_reason VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS entry_policy_snapshot VARCHAR(50) NULL;

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_invited_by_participante
        FOREIGN KEY (invited_by_participante_id) REFERENCES sessao_consumo_participantes(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_invited_by_device
        FOREIGN KEY (invited_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_approved_by_participante
        FOREIGN KEY (approved_by_participante_id) REFERENCES sessao_consumo_participantes(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_approved_by_device
        FOREIGN KEY (approved_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_rejected_by_participante
        FOREIGN KEY (rejected_by_participante_id) REFERENCES sessao_consumo_participantes(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_rejected_by_device
        FOREIGN KEY (rejected_by_device_id) REFERENCES dispositivos_operacionais(id);

