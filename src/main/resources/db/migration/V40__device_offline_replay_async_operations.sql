-- Prompt 40.4: replay assíncrono com operationId + progress tracking + rate-limit por tenant

CREATE TABLE IF NOT EXISTS device_offline_replay_operations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    server_sync_id VARCHAR(120) NOT NULL,
    sync_session_db_id BIGINT NOT NULL,
    operation_id VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    last_progress_at TIMESTAMP WITH TIME ZONE NULL,
    last_progress_percent INTEGER NULL,
    last_progress_event_at TIMESTAMP WITH TIME ZONE NULL,
    last_progress_event_percent INTEGER NULL,
    reason VARCHAR(255) NOT NULL,
    force BOOLEAN NOT NULL DEFAULT false,
    command_status_filter_json JSONB NULL,
    command_type_filter_json JSONB NULL,
    command_ids_json JSONB NULL,
    total_items INTEGER NOT NULL DEFAULT 0,
    pending_items INTEGER NOT NULL DEFAULT 0,
    running_items INTEGER NOT NULL DEFAULT 0,
    succeeded_items INTEGER NOT NULL DEFAULT 0,
    noop_items INTEGER NOT NULL DEFAULT 0,
    blocked_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NULL,
    locked_by VARCHAR(100) NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_offline_replay_op_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_offline_replay_op_session FOREIGN KEY (sync_session_db_id) REFERENCES device_offline_sync_sessions(id),
    CONSTRAINT uk_offline_replay_op_operation_id UNIQUE (tenant_id, operation_id),
    CONSTRAINT chk_offline_replay_op_total_items CHECK (total_items >= 0),
    CONSTRAINT chk_offline_replay_op_progress_percent CHECK (progress_percent >= 0 AND progress_percent <= 100)
);

CREATE INDEX IF NOT EXISTS idx_offline_replay_ops_tenant_status ON device_offline_replay_operations (tenant_id, status, requested_at);
CREATE INDEX IF NOT EXISTS idx_offline_replay_ops_sync_session ON device_offline_replay_operations (tenant_id, server_sync_id, requested_at);
CREATE INDEX IF NOT EXISTS idx_offline_replay_ops_operation_id ON device_offline_replay_operations (tenant_id, operation_id);
CREATE INDEX IF NOT EXISTS idx_offline_replay_ops_requested_at ON device_offline_replay_operations (tenant_id, requested_at);

CREATE TABLE IF NOT EXISTS device_offline_replay_operation_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_db_id BIGINT NOT NULL,
    operation_id VARCHAR(120) NOT NULL,
    sync_session_db_id BIGINT NOT NULL,
    server_sync_id VARCHAR(120) NOT NULL,
    device_offline_command_id BIGINT NOT NULL,
    client_request_id VARCHAR(120) NOT NULL,
    command_type VARCHAR(80) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    item_status VARCHAR(30) NOT NULL,
    eligibility_status VARCHAR(30) NULL,
    eligibility_reason VARCHAR(120) NULL,
    replay_attempt_id BIGINT NULL,
    result_status VARCHAR(30) NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE NULL,
    locked_at TIMESTAMP WITH TIME ZONE NULL,
    locked_by VARCHAR(100) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_offline_replay_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_offline_replay_item_op FOREIGN KEY (operation_db_id) REFERENCES device_offline_replay_operations(id),
    CONSTRAINT fk_offline_replay_item_session FOREIGN KEY (sync_session_db_id) REFERENCES device_offline_sync_sessions(id),
    CONSTRAINT fk_offline_replay_item_command FOREIGN KEY (device_offline_command_id) REFERENCES device_offline_commands(id),
    CONSTRAINT fk_offline_replay_item_attempt FOREIGN KEY (replay_attempt_id) REFERENCES device_offline_command_replay_attempts(id),
    CONSTRAINT uk_offline_replay_item UNIQUE (tenant_id, operation_db_id, device_offline_command_id),
    CONSTRAINT chk_offline_replay_item_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_offline_replay_items_op_status ON device_offline_replay_operation_items (tenant_id, operation_db_id, item_status, id);
CREATE INDEX IF NOT EXISTS idx_offline_replay_items_cmd ON device_offline_replay_operation_items (tenant_id, device_offline_command_id);
CREATE INDEX IF NOT EXISTS idx_offline_replay_items_tenant_status ON device_offline_replay_operation_items (tenant_id, item_status, id);
CREATE INDEX IF NOT EXISTS idx_offline_replay_items_server_sync ON device_offline_replay_operation_items (tenant_id, server_sync_id, id);

ALTER TABLE device_offline_commands
    ADD COLUMN IF NOT EXISTS replay_in_progress BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS current_replay_operation_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_replay_in_progress ON device_offline_commands (tenant_id, replay_in_progress, id);
CREATE INDEX IF NOT EXISTS idx_device_offline_cmd_current_replay_operation ON device_offline_commands (tenant_id, current_replay_operation_id, id);

