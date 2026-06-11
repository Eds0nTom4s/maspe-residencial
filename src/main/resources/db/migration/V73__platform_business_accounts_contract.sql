ALTER TABLE business_accounts
    ADD COLUMN IF NOT EXISTS max_tenants INTEGER;

UPDATE business_accounts
SET max_tenants = COALESCE(max_tenants, 1)
WHERE max_tenants IS NULL;

ALTER TABLE business_accounts
    ALTER COLUMN max_tenants SET DEFAULT 1;

ALTER TABLE business_accounts
    ALTER COLUMN max_tenants SET NOT NULL;

CREATE TABLE IF NOT EXISTS business_account_limit_overrides (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    business_account_id BIGINT NOT NULL,
    max_tenants INTEGER,
    max_instituicoes INTEGER,
    max_unidades_atendimento INTEGER,
    max_dispositivos INTEGER,
    max_produtos INTEGER,
    max_categorias INTEGER,
    max_usuarios INTEGER,
    max_qr_codes INTEGER,
    max_pedidos_mes INTEGER,
    observacao VARCHAR(500),
    configurado_por VARCHAR(120),
    configurado_em TIMESTAMP,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_ba_limit_override_account
        FOREIGN KEY (business_account_id) REFERENCES business_accounts(id)
);

ALTER TABLE business_account_limit_overrides
    ADD COLUMN IF NOT EXISTS max_unidades_atendimento INTEGER;
ALTER TABLE business_account_limit_overrides
    ADD COLUMN IF NOT EXISTS max_categorias INTEGER;
ALTER TABLE business_account_limit_overrides
    ADD COLUMN IF NOT EXISTS max_usuarios INTEGER;
ALTER TABLE business_account_limit_overrides
    ADD COLUMN IF NOT EXISTS max_qr_codes INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ba_limit_override_account
    ON business_account_limit_overrides (business_account_id);

CREATE INDEX IF NOT EXISTS idx_ba_limit_override_account
    ON business_account_limit_overrides (business_account_id);

CREATE TABLE IF NOT EXISTS onboarding_requests (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    nome_solicitante VARCHAR(160),
    telefone VARCHAR(30),
    email VARCHAR(120),
    nome_negocio VARCHAR(160) NOT NULL,
    nif VARCHAR(30),
    tipo_negocio VARCHAR(40),
    plano_id BIGINT,
    business_account_id BIGINT,
    tenant_id BIGINT,
    status VARCHAR(40) NOT NULL,
    status_pagamento VARCHAR(30) NOT NULL,
    valor NUMERIC(19,4),
    moeda VARCHAR(3),
    observacao VARCHAR(500),
    motivo_rejeicao VARCHAR(500),
    approved_at TIMESTAMP,
    rejected_at TIMESTAMP,
    activated_at TIMESTAMP,
    notification_status VARCHAR(40),
    notification_message VARCHAR(500),
    CONSTRAINT fk_onboarding_plano
        FOREIGN KEY (plano_id) REFERENCES planos(id),
    CONSTRAINT fk_onboarding_business_account
        FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_onboarding_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_status
    ON onboarding_requests (status);

CREATE INDEX IF NOT EXISTS idx_onboarding_business_account
    ON onboarding_requests (business_account_id);

CREATE INDEX IF NOT EXISTS idx_onboarding_created_at
    ON onboarding_requests (created_at);
