CREATE TABLE IF NOT EXISTS business_accounts (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    nome VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    nif VARCHAR(30),
    email VARCHAR(120),
    telefone VARCHAR(20),
    estado VARCHAR(20) NOT NULL,
    responsavel_user_id BIGINT,
    observacao VARCHAR(500),
    provisioned_at TIMESTAMP,
    provisioned_by VARCHAR(120),
    CONSTRAINT fk_business_account_responsavel
        FOREIGN KEY (responsavel_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS business_account_members (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    business_account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    CONSTRAINT fk_business_account_member_account
        FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_business_account_member_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_accounts_slug ON business_accounts (slug);
CREATE INDEX IF NOT EXISTS idx_business_accounts_nif ON business_accounts (nif);
CREATE INDEX IF NOT EXISTS idx_business_accounts_estado ON business_accounts (estado);
CREATE INDEX IF NOT EXISTS idx_business_accounts_responsavel ON business_accounts (responsavel_user_id);

CREATE INDEX IF NOT EXISTS idx_business_account_members_account ON business_account_members (business_account_id);
CREATE INDEX IF NOT EXISTS idx_business_account_members_user ON business_account_members (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_business_account_members_account_user
    ON business_account_members (business_account_id, user_id);

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS business_account_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'fk_tenants_business_account'
          AND t.relname = 'tenants'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE tenants
            ADD CONSTRAINT fk_tenants_business_account
            FOREIGN KEY (business_account_id) REFERENCES business_accounts(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tenants_business_account ON tenants (business_account_id);

INSERT INTO business_accounts (
    version,
    created_at,
    updated_at,
    created_by,
    modified_by,
    nome,
    slug,
    nif,
    email,
    telefone,
    estado,
    responsavel_user_id,
    observacao,
    provisioned_at,
    provisioned_by
)
SELECT
    0,
    COALESCE(t.created_at, CURRENT_TIMESTAMP),
    t.updated_at,
    COALESCE(t.created_by, 'flyway-v72'),
    COALESCE(t.modified_by, t.created_by, 'flyway-v72'),
    t.nome,
    t.slug,
    t.nif,
    t.email,
    t.telefone,
    CASE t.estado
        WHEN 'ATIVO' THEN 'ATIVA'
        WHEN 'SUSPENSO' THEN 'SUSPENSA'
        WHEN 'BLOQUEADO' THEN 'BLOQUEADA'
        WHEN 'CANCELADO' THEN 'CANCELADA'
        ELSE 'RASCUNHO'
    END,
    owner_link.user_id,
    'Conta empresarial legada criada automaticamente para o tenant ' || COALESCE(t.tenant_code, CAST(t.id AS VARCHAR)),
    COALESCE(t.provisioned_at, t.created_at, CURRENT_TIMESTAMP),
    COALESCE(t.provisioned_by, t.created_by, 'flyway-v72')
FROM tenants t
LEFT JOIN LATERAL (
    SELECT tu.user_id
    FROM tenant_users tu
    WHERE tu.tenant_id = t.id
      AND tu.role = 'TENANT_OWNER'
      AND tu.estado = 'ATIVO'
    ORDER BY tu.id ASC
    LIMIT 1
) owner_link ON TRUE
WHERE t.business_account_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM business_accounts ba
      WHERE ba.slug = t.slug
  );

UPDATE tenants t
SET business_account_id = ba.id
FROM business_accounts ba
WHERE t.business_account_id IS NULL
  AND ba.slug = t.slug;

WITH ranked_members AS (
    SELECT
        t.business_account_id,
        tu.user_id,
        CASE tu.role
            WHEN 'TENANT_OWNER' THEN 'OWNER'
            WHEN 'TENANT_ADMIN' THEN 'ADMIN'
            WHEN 'TENANT_FINANCE' THEN 'BILLING_MANAGER'
            WHEN 'TENANT_OPERATOR' THEN 'MEMBER'
            WHEN 'TENANT_KITCHEN' THEN 'MEMBER'
            WHEN 'TENANT_CASHIER' THEN 'MEMBER'
            ELSE 'VIEWER'
        END AS role,
        CASE tu.role
            WHEN 'TENANT_OWNER' THEN 1
            WHEN 'TENANT_ADMIN' THEN 2
            WHEN 'TENANT_FINANCE' THEN 3
            WHEN 'TENANT_OPERATOR' THEN 4
            WHEN 'TENANT_KITCHEN' THEN 5
            WHEN 'TENANT_CASHIER' THEN 6
            ELSE 99
        END AS role_priority,
        tu.created_at,
        tu.updated_at,
        tu.created_by,
        tu.modified_by
    FROM tenant_users tu
    JOIN tenants t ON t.id = tu.tenant_id
    WHERE t.business_account_id IS NOT NULL
      AND tu.estado = 'ATIVO'
), deduplicated_members AS (
    SELECT *,
           ROW_NUMBER() OVER (
               PARTITION BY business_account_id, user_id
               ORDER BY role_priority ASC, created_at ASC NULLS LAST, user_id ASC
           ) AS rn
    FROM ranked_members
)
INSERT INTO business_account_members (
    version,
    created_at,
    updated_at,
    created_by,
    modified_by,
    business_account_id,
    user_id,
    role,
    estado
)
SELECT
    0,
    COALESCE(dm.created_at, CURRENT_TIMESTAMP),
    dm.updated_at,
    COALESCE(dm.created_by, 'flyway-v72'),
    COALESCE(dm.modified_by, dm.created_by, 'flyway-v72'),
    dm.business_account_id,
    dm.user_id,
    dm.role,
    'ATIVO'
FROM deduplicated_members dm
WHERE dm.rn = 1
ON CONFLICT (business_account_id, user_id) DO UPDATE
SET role = EXCLUDED.role,
    estado = EXCLUDED.estado,
    updated_at = COALESCE(EXCLUDED.updated_at, CURRENT_TIMESTAMP),
    modified_by = COALESCE(EXCLUDED.modified_by, 'flyway-v72');
