-- Prompt 41: Sessão compartilhada com múltiplos participantes

CREATE TABLE IF NOT EXISTS sessao_consumo_participantes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    sessao_consumo_id BIGINT NOT NULL,
    cliente_consumo_id BIGINT NOT NULL,
    telefone_normalizado VARCHAR(30) NOT NULL,
    nome_exibicao VARCHAR(120) NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NULL,
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    removed_at TIMESTAMP WITH TIME ZONE NULL,
    left_at TIMESTAMP WITH TIME ZONE NULL,
    blocked_at TIMESTAMP WITH TIME ZONE NULL,
    added_by_cliente_consumo_id BIGINT NULL,
    added_by_device_id BIGINT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NULL,
    metadata_json JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_sessao_participante UNIQUE (tenant_id, sessao_consumo_id, cliente_consumo_id),
    CONSTRAINT fk_sessao_participante_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sessao_participante_sessao FOREIGN KEY (sessao_consumo_id) REFERENCES sessoes_consumo(id),
    CONSTRAINT fk_sessao_participante_cliente FOREIGN KEY (cliente_consumo_id) REFERENCES cliente_consumo(id),
    CONSTRAINT fk_sessao_participante_added_by_cliente FOREIGN KEY (added_by_cliente_consumo_id) REFERENCES cliente_consumo(id),
    CONSTRAINT fk_sessao_participante_added_by_device FOREIGN KEY (added_by_device_id) REFERENCES dispositivos_operacionais(id)
);

CREATE INDEX IF NOT EXISTS idx_sessao_participantes_tenant_sessao ON sessao_consumo_participantes (tenant_id, sessao_consumo_id, status);
CREATE INDEX IF NOT EXISTS idx_sessao_participantes_tenant_cliente ON sessao_consumo_participantes (tenant_id, cliente_consumo_id, status);
CREATE INDEX IF NOT EXISTS idx_sessao_participantes_tenant_status ON sessao_consumo_participantes (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sessao_participantes_telefone ON sessao_consumo_participantes (tenant_id, telefone_normalizado);

ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS sessao_participante_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS cliente_consumo_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pedidos_sessao_participante') THEN
        ALTER TABLE pedidos
            ADD CONSTRAINT fk_pedidos_sessao_participante
                FOREIGN KEY (sessao_participante_id) REFERENCES sessao_consumo_participantes(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pedidos_cliente_consumo') THEN
        ALTER TABLE pedidos
            ADD CONSTRAINT fk_pedidos_cliente_consumo
                FOREIGN KEY (cliente_consumo_id) REFERENCES cliente_consumo(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pedidos_sessao_participante ON pedidos (sessao_participante_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_cliente_consumo ON pedidos (cliente_consumo_id);
