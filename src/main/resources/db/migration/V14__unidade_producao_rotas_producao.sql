-- Prompt 17: UnidadeProducao + Rotas de produção por CategoriaProduto + SubPedido -> UnidadeProducao

-- =============================================================================
-- 1) unidades_producao
-- =============================================================================

CREATE TABLE IF NOT EXISTS unidades_producao (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    instituicao_id bigint NOT NULL,
    unidade_atendimento_id bigint NULL,
    nome varchar(120) NOT NULL,
    codigo varchar(40) NOT NULL,
    tipo varchar(30) NOT NULL,
    ativo boolean NOT NULL DEFAULT true,
    ordem integer NOT NULL DEFAULT 0,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    atualizado_em timestamp(6) NULL
);

ALTER TABLE unidades_producao
    ADD CONSTRAINT fk_unidades_producao_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT fk_unidades_producao_instituicao
        FOREIGN KEY (instituicao_id) REFERENCES instituicoes (id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT fk_unidades_producao_unidade_atendimento
        FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento (id);

-- Permite múltiplas instituições por tenant (código único por tenant+instituição)
CREATE UNIQUE INDEX IF NOT EXISTS ux_unidades_producao_tenant_instituicao_codigo
    ON unidades_producao (tenant_id, instituicao_id, codigo);

CREATE INDEX IF NOT EXISTS idx_unidades_producao_tenant_id ON unidades_producao (tenant_id);
CREATE INDEX IF NOT EXISTS idx_unidades_producao_tenant_ativo ON unidades_producao (tenant_id, ativo);
CREATE INDEX IF NOT EXISTS idx_unidades_producao_tenant_instituicao ON unidades_producao (tenant_id, instituicao_id);
CREATE INDEX IF NOT EXISTS idx_unidades_producao_tenant_unidade_atendimento ON unidades_producao (tenant_id, unidade_atendimento_id);

-- =============================================================================
-- 2) rotas_producao_categoria
-- =============================================================================

CREATE TABLE IF NOT EXISTS rotas_producao_categoria (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    categoria_produto_id bigint NOT NULL,
    unidade_producao_id bigint NOT NULL,
    ativo boolean NOT NULL DEFAULT true,
    prioridade integer NOT NULL DEFAULT 0,
    criado_em timestamp(6) NOT NULL DEFAULT now(),
    atualizado_em timestamp(6) NULL
);

ALTER TABLE rotas_producao_categoria
    ADD CONSTRAINT fk_rota_categoria_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE rotas_producao_categoria
    ADD CONSTRAINT fk_rota_categoria_categoria
        FOREIGN KEY (categoria_produto_id) REFERENCES categoria_produtos (id);

ALTER TABLE rotas_producao_categoria
    ADD CONSTRAINT fk_rota_categoria_unidade_producao
        FOREIGN KEY (unidade_producao_id) REFERENCES unidades_producao (id);

CREATE INDEX IF NOT EXISTS idx_rota_categoria_tenant_id ON rotas_producao_categoria (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rota_categoria_tenant_categoria ON rotas_producao_categoria (tenant_id, categoria_produto_id);
CREATE INDEX IF NOT EXISTS idx_rota_categoria_tenant_unidade ON rotas_producao_categoria (tenant_id, unidade_producao_id);

-- Uma rota ativa por categoria por tenant (enforcement via índice parcial)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname = 'ux_rota_categoria_ativa'
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX ux_rota_categoria_ativa
                 ON rotas_producao_categoria (tenant_id, categoria_produto_id)
                 WHERE ativo = true';
    END IF;
END $$;

-- =============================================================================
-- 3) sub_pedidos.unidade_producao_id
-- =============================================================================

ALTER TABLE sub_pedidos
    ADD COLUMN IF NOT EXISTS unidade_producao_id bigint;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sub_pedidos_unidade_producao') THEN
        ALTER TABLE sub_pedidos
            ADD CONSTRAINT fk_sub_pedidos_unidade_producao
                FOREIGN KEY (unidade_producao_id) REFERENCES unidades_producao (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_subpedido_tenant_unidade_producao
    ON sub_pedidos (tenant_id, unidade_producao_id);

-- =============================================================================
-- 4) Backfill: criar unidade default por tenant+instituição e associar subpedidos antigos
-- =============================================================================

-- Cria unidade "Produção Geral" para cada (tenant, instituicao) visto em sub_pedidos
INSERT INTO unidades_producao (tenant_id, instituicao_id, unidade_atendimento_id, nome, codigo, tipo, ativo, ordem, criado_em)
SELECT DISTINCT
    sp.tenant_id,
    ua.instituicao_id,
    sp.unidade_atendimento_id,
    'Produção Geral' AS nome,
    'GERAL' AS codigo,
    'OUTRO' AS tipo,
    true AS ativo,
    0 AS ordem,
    now() AS criado_em
FROM sub_pedidos sp
JOIN unidades_atendimento ua ON ua.id = sp.unidade_atendimento_id
WHERE ua.instituicao_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Vincula subpedidos antigos à unidade "GERAL" do seu (tenant, instituicao)
UPDATE sub_pedidos sp
SET unidade_producao_id = up.id
FROM unidades_atendimento ua,
     unidades_producao up
WHERE sp.unidade_producao_id IS NULL
  AND sp.unidade_atendimento_id = ua.id
  AND up.tenant_id = sp.tenant_id
  AND up.instituicao_id = ua.instituicao_id
  AND up.codigo = 'GERAL';

-- Se ainda restar subpedido sem unidade_producao_id, mantemos nullable por compatibilidade.
-- Novos fluxos tenant-aware devem preencher via service/rota.
