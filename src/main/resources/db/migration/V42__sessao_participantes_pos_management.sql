-- Prompt 41.1: gestão assistida de participantes no POS/device

ALTER TABLE sessao_consumo_participantes
    ADD COLUMN IF NOT EXISTS promoted_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS demoted_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS promoted_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS demoted_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS removed_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS blocked_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS restored_by_device_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS role_changed_reason VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS status_changed_reason VARCHAR(255) NULL;

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_promoted_by_device
        FOREIGN KEY (promoted_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_demoted_by_device
        FOREIGN KEY (demoted_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_removed_by_device
        FOREIGN KEY (removed_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_blocked_by_device
        FOREIGN KEY (blocked_by_device_id) REFERENCES dispositivos_operacionais(id);

ALTER TABLE sessao_consumo_participantes
    ADD CONSTRAINT IF NOT EXISTS fk_sessao_participante_restored_by_device
        FOREIGN KEY (restored_by_device_id) REFERENCES dispositivos_operacionais(id);

