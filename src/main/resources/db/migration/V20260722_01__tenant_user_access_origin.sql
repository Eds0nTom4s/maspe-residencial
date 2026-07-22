-- Proveniência mínima para distinguir o TENANT_OWNER derivado da governação
-- empresarial de roles operacionais concedidas explicitamente.

ALTER TABLE tenant_users
    ADD COLUMN access_origin varchar(40) NOT NULL DEFAULT 'EXPLICIT';

-- No estado histórico, a role TENANT_OWNER do responsável principal actual é
-- a melhor evidência canónica de acesso derivado. Outras roles permanecem
-- explícitas e nunca serão revogadas por owner replacement.
UPDATE tenant_users tu
SET access_origin = 'BUSINESS_ACCOUNT_OWNER'
FROM tenants t
JOIN business_accounts ba ON ba.id = t.business_account_id
WHERE tu.tenant_id = t.id
  AND tu.user_id = ba.responsavel_user_id
  AND tu.role = 'TENANT_OWNER';

ALTER TABLE tenant_users
    DROP CONSTRAINT uk_tenant_user_unique;

ALTER TABLE tenant_users
    ADD CONSTRAINT uk_tenant_user_unique
    UNIQUE (tenant_id, user_id, role, access_origin);

CREATE INDEX idx_tenant_users_access_origin
    ON tenant_users (tenant_id, role, access_origin, estado);
