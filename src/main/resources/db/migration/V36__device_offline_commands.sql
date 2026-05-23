-- Prompt 40: POS offline-first mínimo (sync de comandos)

CREATE TABLE IF NOT EXISTS device_offline_commands (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NOT NULL,
    dispositivo_operacional_id BIGINT NOT NULL,
    client_request_id VARCHAR(120) NOT NULL,
    command_type VARCHAR(80) NOT NULL,
    command_version VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at TIMESTAMP WITH TIME ZONE NULL,
    failed_at TIMESTAMP WITH TIME ZONE NULL,
    local_created_at TIMESTAMP WITH TIME ZONE NULL,
    local_sequence BIGINT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    result_json JSONB NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    conflict_code VARCHAR(100) NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    idempotency_scope VARCHAR(150) NOT NULL,
    created_entity_type VARCHAR(80) NULL,
    created_entity_id BIGINT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_device_offline_cmd UNIQUE (tenant_id, dispositivo_operacional_id, client_request_id),
    CONSTRAINT fk_device_offline_cmd_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_device_offline_cmd_unidade FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id),
    CONSTRAINT fk_device_offline_cmd_device FOREIGN KEY (dispositivo_operacional_id) REFERENCES dispositivos_operacionais(id),
    CONSTRAINT chk_device_offline_retry_nonneg CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_tenant_device ON device_offline_commands (tenant_id, dispositivo_operacional_id);
CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_status ON device_offline_commands (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_received_at ON device_offline_commands (tenant_id, received_at);
CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_client_request ON device_offline_commands (tenant_id, dispositivo_operacional_id, client_request_id);

