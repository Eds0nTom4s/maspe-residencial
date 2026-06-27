-- Public menu hardening: banner, max items per order, product images

-- Banner URL and max items per order on tenant cardapio config
ALTER TABLE tenant_cardapio_configs
    ADD COLUMN IF NOT EXISTS url_banner VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS max_itens_por_pedido INTEGER NULL;

-- Product images gallery (up to 4 per product)
CREATE TABLE IF NOT EXISTS produto_imagens (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,
    legenda VARCHAR(200) NULL,
    version BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    CONSTRAINT fk_produto_imagens_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_produto_imagens_produto FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_produto_imagens_produto_ordem
    ON produto_imagens (produto_id, ordem);

CREATE INDEX IF NOT EXISTS idx_produto_imagens_tenant_produto
    ON produto_imagens (tenant_id, produto_id);
