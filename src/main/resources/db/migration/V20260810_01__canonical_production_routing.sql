-- Canonical production routing: UnidadeProducao becomes authoritative for new orders.
-- Legacy Cozinha remains readable for historical rows, but is no longer mandatory.

ALTER TABLE sub_pedidos
    ALTER COLUMN cozinha_id DROP NOT NULL;

ALTER TABLE subpedido_event_log
    ALTER COLUMN cozinha_id DROP NOT NULL;

-- Composite keys make tenant ownership enforceable by the database.
ALTER TABLE instituicoes
    ADD CONSTRAINT uq_instituicoes_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE unidades_atendimento
    ADD CONSTRAINT uq_unidades_atendimento_instituicao_id UNIQUE (instituicao_id, id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT uq_unidades_producao_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT uq_unidades_producao_tenant_instituicao_id
        UNIQUE (tenant_id, instituicao_id, id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT fk_unidades_producao_instituicao_tenant
        FOREIGN KEY (tenant_id, instituicao_id)
        REFERENCES instituicoes (tenant_id, id);

ALTER TABLE unidades_producao
    ADD CONSTRAINT fk_unidades_producao_atendimento_instituicao
        FOREIGN KEY (instituicao_id, unidade_atendimento_id)
        REFERENCES unidades_atendimento (instituicao_id, id);

ALTER TABLE rotas_producao_categoria
    ADD COLUMN instituicao_id bigint;

UPDATE rotas_producao_categoria rota
SET instituicao_id = unidade.instituicao_id
FROM unidades_producao unidade
WHERE unidade.id = rota.unidade_producao_id
  AND rota.instituicao_id IS NULL;

ALTER TABLE rotas_producao_categoria
    ALTER COLUMN instituicao_id SET NOT NULL;

DROP INDEX IF EXISTS ux_rota_categoria_ativa;

CREATE UNIQUE INDEX ux_rota_categoria_instituicao_ativa
    ON rotas_producao_categoria (tenant_id, instituicao_id, categoria_produto_id)
    WHERE ativo = true;

ALTER TABLE rotas_producao_categoria
    ADD CONSTRAINT fk_rotas_producao_categoria_tenant
        FOREIGN KEY (tenant_id, categoria_produto_id)
        REFERENCES categoria_produtos (tenant_id, id);

ALTER TABLE rotas_producao_categoria
    ADD CONSTRAINT fk_rotas_producao_unidade_tenant_instituicao
        FOREIGN KEY (tenant_id, instituicao_id, unidade_producao_id)
        REFERENCES unidades_producao (tenant_id, instituicao_id, id);

CREATE INDEX idx_rotas_producao_tenant_instituicao
    ON rotas_producao_categoria (tenant_id, instituicao_id);

ALTER TABLE sub_pedidos
    ADD CONSTRAINT fk_sub_pedidos_unidade_producao_tenant
        FOREIGN KEY (tenant_id, unidade_producao_id)
        REFERENCES unidades_producao (tenant_id, id);

COMMENT ON COLUMN sub_pedidos.cozinha_id IS
    'Compatibilidade legada; novos pedidos usam unidade_producao_id como autoridade.';
