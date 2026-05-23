-- Prompt 40.2: Sync session header + observabilidade/troubleshooting

CREATE TABLE IF NOT EXISTS device_offline_sync_sessions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NOT NULL,
    dispositivo_operacional_id BIGINT NOT NULL,
    sync_session_id VARCHAR(120) NOT NULL,
    server_sync_id VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    app_version VARCHAR(80) NULL,
    device_local_time TIMESTAMP WITH TIME ZONE NULL,
    offline_started_at TIMESTAMP WITH TIME ZONE NULL,
    offline_ended_at TIMESTAMP WITH TIME ZONE NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_processing_at TIMESTAMP WITH TIME ZONE NULL,
    finished_processing_at TIMESTAMP WITH TIME ZONE NULL,
    duration_ms BIGINT NULL,
    total_commands INTEGER NOT NULL DEFAULT 0,
    applied_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    conflict_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    dependency_failed_count INTEGER NOT NULL DEFAULT 0,
    payload_limit_rejected_count INTEGER NOT NULL DEFAULT 0,
    local_ref_count INTEGER NOT NULL DEFAULT 0,
    total_payload_bytes INTEGER NULL,
    max_command_payload_bytes INTEGER NULL,
    client_ip VARCHAR(100) NULL,
    user_agent VARCHAR(255) NULL,
    summary_json JSONB NULL,
    error_summary_json JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_offline_sync_session UNIQUE (tenant_id, dispositivo_operacional_id, sync_session_id),
    CONSTRAINT uk_offline_server_sync_id UNIQUE (tenant_id, server_sync_id),
    CONSTRAINT fk_offline_sync_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_offline_sync_unidade FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id),
    CONSTRAINT fk_offline_sync_device FOREIGN KEY (dispositivo_operacional_id) REFERENCES dispositivos_operacionais(id),
    CONSTRAINT chk_offline_sync_total_nonneg CHECK (total_commands >= 0),
    CONSTRAINT chk_offline_sync_applied_nonneg CHECK (applied_count >= 0),
    CONSTRAINT chk_offline_sync_dup_nonneg CHECK (duplicate_count >= 0),
    CONSTRAINT chk_offline_sync_rej_nonneg CHECK (rejected_count >= 0),
    CONSTRAINT chk_offline_sync_conf_nonneg CHECK (conflict_count >= 0),
    CONSTRAINT chk_offline_sync_failed_nonneg CHECK (failed_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_tenant_received ON device_offline_sync_sessions (tenant_id, received_at);
CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_tenant_status ON device_offline_sync_sessions (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_tenant_device ON device_offline_sync_sessions (tenant_id, dispositivo_operacional_id);
CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_tenant_unidade ON device_offline_sync_sessions (tenant_id, unidade_atendimento_id);
CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_app_version ON device_offline_sync_sessions (tenant_id, app_version);
CREATE INDEX IF NOT EXISTS idx_offline_sync_sessions_server_sync_id ON device_offline_sync_sessions (tenant_id, server_sync_id);

ALTER TABLE device_offline_commands
    ADD COLUMN IF NOT EXISTS sync_session_db_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS server_sync_id VARCHAR(120) NULL;

ALTER TABLE device_offline_commands
    ADD CONSTRAINT IF NOT EXISTS fk_device_offline_cmd_session
        FOREIGN KEY (sync_session_db_id) REFERENCES device_offline_sync_sessions(id);

CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_sync_session ON device_offline_commands (tenant_id, sync_session_db_id);
CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_server_sync ON device_offline_commands (tenant_id, server_sync_id);

