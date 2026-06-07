ALTER TABLE planos
    ADD COLUMN IF NOT EXISTS max_categorias INTEGER;

UPDATE planos
SET max_categorias = 20
WHERE max_categorias IS NULL;

ALTER TABLE planos
    ALTER COLUMN max_categorias SET NOT NULL;

ALTER TABLE tenant_limite_overrides
    ADD COLUMN IF NOT EXISTS max_categorias INTEGER;

CREATE TABLE IF NOT EXISTS tenant_cardapio_configs (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    tenant_id BIGINT NOT NULL,
    cardapio_publicado BOOLEAN NOT NULL DEFAULT FALSE,
    cardapio_publicado_em TIMESTAMP,
    cardapio_publicado_por_user_id BIGINT,
    cardapio_despublicado_em TIMESTAMP,
    cardapio_despublicado_por_user_id BIGINT,
    cardapio_motivo_despublicacao VARCHAR(500),
    cardapio_atualizado_em TIMESTAMP,
    CONSTRAINT uk_tenant_cardapio_configs_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_cardapio_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_cardapio_config_tenant
    ON tenant_cardapio_configs(tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_cardapio_config_publicado
    ON tenant_cardapio_configs(cardapio_publicado);

INSERT INTO tenant_cardapio_configs (
    tenant_id,
    cardapio_publicado,
    cardapio_atualizado_em,
    created_at,
    updated_at
)
SELECT t.id, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_cardapio_configs c
    WHERE c.tenant_id = t.id
);
