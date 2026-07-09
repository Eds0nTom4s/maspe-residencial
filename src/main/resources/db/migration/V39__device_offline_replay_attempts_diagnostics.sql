-- Prompt 40.3: replay controlado por sync session + export diagnóstico sanitizado

CREATE TABLE IF NOT EXISTS device_offline_command_replay_attempts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    sync_session_db_id BIGINT NOT NULL,
    server_sync_id VARCHAR(120) NOT NULL,
    device_offline_command_id BIGINT NOT NULL,
    dispositivo_operacional_id BIGINT NOT NULL,
    client_request_id VARCHAR(120) NOT NULL,
    command_type VARCHAR(80) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    replay_status VARCHAR(30) NOT NULL,
    eligibility_status VARCHAR(30) NOT NULL,
    eligibility_reason VARCHAR(120) NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    requested_by BIGINT NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    result_status VARCHAR(30) NULL,
    result_json JSONB NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    audit_reason VARCHAR(255) NULL,
    created_entity_type VARCHAR(80) NULL,
    created_entity_id BIGINT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_offline_replay_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_offline_replay_session FOREIGN KEY (sync_session_db_id) REFERENCES device_offline_sync_sessions(id),
    CONSTRAINT fk_offline_replay_command FOREIGN KEY (device_offline_command_id) REFERENCES device_offline_commands(id),
    CONSTRAINT fk_offline_replay_device FOREIGN KEY (dispositivo_operacional_id) REFERENCES dispositivos_operacionais(id),
    CONSTRAINT chk_offline_replay_attempt_number CHECK (attempt_number >= 1)
);

CREATE INDEX IF NOT EXISTS idx_offline_replay_attempts_tenant_session ON device_offline_command_replay_attempts (tenant_id, server_sync_id, requested_at);
CREATE INDEX IF NOT EXISTS idx_offline_replay_attempts_command ON device_offline_command_replay_attempts (tenant_id, device_offline_command_id, requested_at);
CREATE INDEX IF NOT EXISTS idx_offline_replay_attempts_status ON device_offline_command_replay_attempts (tenant_id, replay_status, requested_at);

ALTER TABLE device_offline_commands
    ADD COLUMN IF NOT EXISTS replay_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_replay_attempt_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS last_replay_attempt_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_device_offline_cmd_last_replay_attempt'
    ) THEN
        ALTER TABLE device_offline_commands
            ADD CONSTRAINT fk_device_offline_cmd_last_replay_attempt
                FOREIGN KEY (last_replay_attempt_id) REFERENCES device_offline_command_replay_attempts(id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_last_replay_attempt ON device_offline_commands (tenant_id, last_replay_attempt_id);
