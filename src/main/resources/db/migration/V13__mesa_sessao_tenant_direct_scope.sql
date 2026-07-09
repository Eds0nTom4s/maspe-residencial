-- Prompt 16: Escopo direto por tenant em Mesa e SessaoConsumo
-- Objetivo:
-- - adicionar mesas.tenant_id e sessoes_consumo.tenant_id
-- - backfill a partir das relações existentes
-- - aplicar NOT NULL + FK + índices para queries tenant-scoped

-- =============================================================================
-- PARTE A — mesas
-- =============================================================================

ALTER TABLE mesas
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- Backfill: Mesa -> UnidadeAtendimento -> Instituicao -> Tenant
UPDATE mesas m
SET tenant_id = i.tenant_id
FROM unidades_atendimento ua
JOIN instituicoes i ON i.id = ua.instituicao_id
WHERE m.tenant_id IS NULL
  AND m.unidade_atendimento_id = ua.id;

-- Backfill alternativo (se unidade não estiver preenchida): Mesa -> Instituicao -> Tenant
UPDATE mesas m
SET tenant_id = i.tenant_id
FROM instituicoes i
WHERE m.tenant_id IS NULL
  AND m.instituicao_id IS NOT NULL
  AND m.instituicao_id = i.id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM mesas WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Backfill mesas.tenant_id falhou: existem mesas sem tenant_id';
    END IF;
END $$;

ALTER TABLE mesas
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE mesas
    ADD CONSTRAINT fk_mesas_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

CREATE INDEX IF NOT EXISTS idx_mesas_tenant_id ON mesas (tenant_id);
CREATE INDEX IF NOT EXISTS idx_mesas_tenant_unidade ON mesas (tenant_id, unidade_atendimento_id);
CREATE INDEX IF NOT EXISTS idx_mesas_tenant_ativa ON mesas (tenant_id, ativa);

-- =============================================================================
-- PARTE B — sessoes_consumo
-- =============================================================================

ALTER TABLE sessoes_consumo
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- Backfill preferencial: SessaoConsumo -> Instituicao -> Tenant
UPDATE sessoes_consumo s
SET tenant_id = i.tenant_id
FROM instituicoes i
WHERE s.tenant_id IS NULL
  AND s.instituicao_id IS NOT NULL
  AND s.instituicao_id = i.id;

-- Fallback: SessaoConsumo -> Mesa -> Tenant
UPDATE sessoes_consumo s
SET tenant_id = m.tenant_id
FROM mesas m
WHERE s.tenant_id IS NULL
  AND s.mesa_id IS NOT NULL
  AND s.mesa_id = m.id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sessoes_consumo WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Backfill sessoes_consumo.tenant_id falhou: existem sessoes_consumo sem tenant_id';
    END IF;
END $$;

ALTER TABLE sessoes_consumo
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE sessoes_consumo
    ADD CONSTRAINT fk_sessoes_consumo_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

CREATE INDEX IF NOT EXISTS idx_sessoes_consumo_tenant_id ON sessoes_consumo (tenant_id);
CREATE INDEX IF NOT EXISTS idx_sessoes_consumo_tenant_status ON sessoes_consumo (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sessoes_consumo_tenant_mesa_status ON sessoes_consumo (tenant_id, mesa_id, status);
CREATE INDEX IF NOT EXISTS idx_sessoes_consumo_tenant_instituicao ON sessoes_consumo (tenant_id, instituicao_id);
CREATE INDEX IF NOT EXISTS idx_sessoes_consumo_tenant_aberta_em ON sessoes_consumo (tenant_id, aberta_em);

