-- Prompt 40.1: localRef/dependsências intra-batch + limites explícitos de payload

ALTER TABLE device_offline_commands
    ADD COLUMN IF NOT EXISTS depends_on_client_request_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS dependency_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS payload_size_bytes INTEGER,
    ADD COLUMN IF NOT EXISTS command_index INTEGER;

CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_dependency
    ON device_offline_commands (tenant_id, dispositivo_operacional_id, depends_on_client_request_id);

