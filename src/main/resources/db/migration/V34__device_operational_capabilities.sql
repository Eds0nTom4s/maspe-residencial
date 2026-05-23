-- Prompt 39.2: Device operational capabilities (tenant-aware)

CREATE TABLE IF NOT EXISTS device_operational_capabilities (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    dispositivo_operacional_id BIGINT NOT NULL,
    capability VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    source_template_id BIGINT NULL,
    metadata_json JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_device_capability UNIQUE (tenant_id, dispositivo_operacional_id, capability),
    CONSTRAINT fk_device_capability_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_device_capability_device FOREIGN KEY (dispositivo_operacional_id) REFERENCES dispositivos_operacionais(id)
);

CREATE INDEX IF NOT EXISTS idx_device_cap_tenant_device ON device_operational_capabilities (tenant_id, dispositivo_operacional_id);
CREATE INDEX IF NOT EXISTS idx_device_cap_tenant_capability ON device_operational_capabilities (tenant_id, capability);

