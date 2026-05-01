ALTER TABLE variacoes_produto ADD COLUMN IF NOT EXISTS tamanho VARCHAR(30);
ALTER TABLE variacoes_produto ADD COLUMN IF NOT EXISTS cor VARCHAR(50);
ALTER TABLE variacoes_produto ADD COLUMN IF NOT EXISTS sku VARCHAR(80);
ALTER TABLE variacoes_produto ADD COLUMN IF NOT EXISTS preco NUMERIC(10, 2);

CREATE UNIQUE INDEX IF NOT EXISTS idx_variacao_sku ON variacoes_produto (sku) WHERE sku IS NOT NULL;

ALTER TABLE itens_pedido ADD COLUMN IF NOT EXISTS variacao_produto_id BIGINT;
ALTER TABLE itens_pedido ADD COLUMN IF NOT EXISTS personalized_name VARCHAR(80);
ALTER TABLE itens_pedido ADD COLUMN IF NOT EXISTS qr_identity_enabled BOOLEAN;
ALTER TABLE itens_pedido ADD COLUMN IF NOT EXISTS premium_packaging BOOLEAN;
ALTER TABLE itens_pedido ADD COLUMN IF NOT EXISTS qr_identity_token_hash VARCHAR(128);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_item_pedido_variacao'
          AND table_name = 'itens_pedido'
    ) THEN
        ALTER TABLE itens_pedido
            ADD CONSTRAINT fk_item_pedido_variacao
            FOREIGN KEY (variacao_produto_id) REFERENCES variacoes_produto (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS store_order_metadata (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    pedido_id BIGINT NOT NULL UNIQUE REFERENCES pedidos (id),
    socio_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) UNIQUE,
    metodo_pagamento VARCHAR(30),
    payment_url VARCHAR(500),
    entidade VARCHAR(20),
    referencia VARCHAR(40),
    endereco_entrega VARCHAR(500),
    notas VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_store_order_socio ON store_order_metadata (socio_id);

CREATE TABLE IF NOT EXISTS store_analytics_events (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    event_type VARCHAR(60) NOT NULL,
    socio_id VARCHAR(100),
    product_id BIGINT,
    order_id BIGINT,
    timestamp TIMESTAMP NOT NULL,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_store_analytics_event_type ON store_analytics_events (event_type);
CREATE INDEX IF NOT EXISTS idx_store_analytics_socio ON store_analytics_events (socio_id);
CREATE INDEX IF NOT EXISTS idx_store_analytics_timestamp ON store_analytics_events (timestamp);

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY(con.conkey)
    WHERE rel.relname = 'pagamentos_gateway'
      AND att.attname = 'tipo_pagamento'
      AND con.contype = 'c'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE pagamentos_gateway DROP CONSTRAINT %I', constraint_name);
    END IF;

    ALTER TABLE pagamentos_gateway
        ADD CONSTRAINT chk_pagamentos_gateway_tipo_pagamento
        CHECK (tipo_pagamento IN ('PRE_PAGO', 'POS_PAGO', 'STORE_PEDIDO'));
END $$;
