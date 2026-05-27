-- Hardening: garantir constraint única em tenant_users mesmo quando baseline V1 já criou a tabela
-- Motivo: V1 cria tenant_users sem UNIQUE(tenant_id,user_id,role). V2 define a constraint no CREATE TABLE,
-- mas em bancos onde a tabela já existe, o CREATE TABLE IF NOT EXISTS não aplica a constraint.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'uk_tenant_user_unique'
          AND t.relname = 'tenant_users'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE tenant_users
            ADD CONSTRAINT uk_tenant_user_unique UNIQUE (tenant_id, user_id, role);
    END IF;
END $$;

