CREATE TABLE IF NOT EXISTS tenant_operational_modules_configs (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    tenant_id BIGINT NOT NULL,
    sessao_consumo_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pedido_direto_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mesas_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    qr_mesa_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    caixa_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    kds_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    configured_by_platform_user_id BIGINT,
    configured_at TIMESTAMP,
    CONSTRAINT fk_tenant_operational_modules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_tenant_operational_modules_tenant UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS tenant_sessao_consumo_configs (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    tenant_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    permitir_pre_pago BOOLEAN NOT NULL DEFAULT TRUE,
    permitir_pos_pago BOOLEAN NOT NULL DEFAULT TRUE,
    tipo_sessao_padrao VARCHAR(20) NOT NULL DEFAULT 'POS_PAGO',
    exigir_saldo_para_pedido BOOLEAN NOT NULL DEFAULT FALSE,
    permitir_modo_anonimo BOOLEAN NOT NULL DEFAULT TRUE,
    permitir_sessao_sem_mesa BOOLEAN NOT NULL DEFAULT TRUE,
    permitir_sessao_com_mesa BOOLEAN NOT NULL DEFAULT TRUE,
    expiracao_horas INTEGER NOT NULL DEFAULT 12,
    updated_by_user_id BIGINT,
    CONSTRAINT fk_tenant_sessao_consumo_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_tenant_sessao_consumo_config_tenant UNIQUE (tenant_id)
);

ALTER TABLE pedidos ALTER COLUMN sessao_consumo_id DROP NOT NULL;
