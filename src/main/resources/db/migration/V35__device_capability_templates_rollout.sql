-- Prompt 39.3: Templates + rollout de device capabilities por unidade/device-type

-- Evolução: marcação template-managed em device_operational_capabilities
ALTER TABLE device_operational_capabilities
    ADD COLUMN IF NOT EXISTS source_template_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS source_rollout_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS template_managed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS manual_override BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS template_applied_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX IF NOT EXISTS idx_device_cap_template_managed ON device_operational_capabilities (tenant_id, template_managed);

-- Templates
CREATE TABLE IF NOT EXISTS device_capability_templates (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    target_device_type VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    is_system_default BOOLEAN NOT NULL DEFAULT false,
    version INTEGER NOT NULL DEFAULT 1,
    metadata_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_device_cap_tpl UNIQUE (tenant_id, code),
    CONSTRAINT fk_device_cap_tpl_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_device_cap_tpl_tenant_status ON device_capability_templates (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_device_cap_tpl_tenant_target ON device_capability_templates (tenant_id, target_device_type);

CREATE TABLE IF NOT EXISTS device_capability_template_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    capability VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    override_reason VARCHAR(255) NULL,
    metadata_json JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_device_cap_tpl_item UNIQUE (tenant_id, template_id, capability),
    CONSTRAINT fk_device_cap_tpl_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_device_cap_tpl_item_tpl FOREIGN KEY (template_id) REFERENCES device_capability_templates(id) ON DELETE CASCADE
);

-- Rollouts (histórico síncrono)
CREATE TABLE IF NOT EXISTS device_capability_rollouts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NOT NULL,
    rollout_mode VARCHAR(50) NOT NULL,
    overwrite_mode VARCHAR(50) NOT NULL,
    target_device_type VARCHAR(50) NULL,
    dry_run BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(30) NOT NULL,
    total_devices_targeted INTEGER NOT NULL DEFAULT 0,
    total_capabilities_created INTEGER NOT NULL DEFAULT 0,
    total_capabilities_updated INTEGER NOT NULL DEFAULT 0,
    total_capabilities_skipped INTEGER NOT NULL DEFAULT 0,
    total_errors INTEGER NOT NULL DEFAULT 0,
    result_json JSONB NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    created_by BIGINT NULL,
    CONSTRAINT fk_device_cap_rollout_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_device_cap_rollout_tpl FOREIGN KEY (template_id) REFERENCES device_capability_templates(id),
    CONSTRAINT fk_device_cap_rollout_unidade FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id)
);

CREATE INDEX IF NOT EXISTS idx_device_cap_rollout_tenant ON device_capability_rollouts (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_device_cap_rollout_tpl ON device_capability_rollouts (tenant_id, template_id);

