-- Prompt 23: seleção explícita de unidade de produção + base para sync read-only

-- 1) Seleção de unidade de produção por usuário dentro do tenant
CREATE TABLE IF NOT EXISTS tenant_user_production_scopes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    unidade_producao_id BIGINT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP NULL
);

ALTER TABLE tenant_user_production_scopes
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NOT NULL DEFAULT now();

ALTER TABLE tenant_user_production_scopes
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) NULL;

ALTER TABLE tenant_user_production_scopes
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE tenant_user_production_scopes
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

ALTER TABLE tenant_user_production_scopes
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tenant_user_production_scopes
    ADD CONSTRAINT fk_tups_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE tenant_user_production_scopes
    ADD CONSTRAINT fk_tups_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE tenant_user_production_scopes
    ADD CONSTRAINT fk_tups_unidade_producao FOREIGN KEY (unidade_producao_id) REFERENCES unidades_producao(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tups_tenant_user
    ON tenant_user_production_scopes (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_tups_tenant
    ON tenant_user_production_scopes (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tups_tenant_user
    ON tenant_user_production_scopes (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_tups_tenant_unidade_producao
    ON tenant_user_production_scopes (tenant_id, unidade_producao_id);

-- 2) Preferência de unidade de produção por dispositivo (opcional)
ALTER TABLE dispositivos_operacionais
    ADD COLUMN IF NOT EXISTS unidade_producao_id BIGINT NULL;

ALTER TABLE dispositivos_operacionais
    ADD CONSTRAINT fk_dispositivo_unidade_producao
        FOREIGN KEY (unidade_producao_id) REFERENCES unidades_producao(id);

CREATE INDEX IF NOT EXISTS idx_dispositivo_tenant_unidade_producao
    ON dispositivos_operacionais (tenant_id, unidade_producao_id);
