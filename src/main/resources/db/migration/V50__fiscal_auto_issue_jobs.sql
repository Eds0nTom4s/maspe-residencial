-- Prompt 43.1 — Automação fiscal controlada pós-pagamento (fila persistida)

CREATE TABLE IF NOT EXISTS fiscal_auto_issue_jobs (
    id BIGSERIAL PRIMARY KEY,

    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NULL,
    pedido_id BIGINT NOT NULL,
    pagamento_id BIGINT NOT NULL,
    sessao_consumo_id BIGINT NULL,
    caixa_operador_session_id BIGINT NULL,

    source VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    trigger_type VARCHAR(80) NOT NULL,

    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE NULL,

    locked_at TIMESTAMP WITH TIME ZONE NULL,
    locked_by VARCHAR(120) NULL,

    idempotency_key VARCHAR(160) NOT NULL,

    error_code VARCHAR(120) NULL,
    error_message TEXT NULL,

    fiscal_document_id BIGINT NULL,

    processed_at TIMESTAMP WITH TIME ZONE NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_unidade
        FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos_gateway(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_sessao
        FOREIGN KEY (sessao_consumo_id) REFERENCES sessoes_consumo(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_caixa
        FOREIGN KEY (caixa_operador_session_id) REFERENCES caixa_operador_sessions(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT fk_fiscal_auto_issue_jobs_fiscal_document
        FOREIGN KEY (fiscal_document_id) REFERENCES fiscal_documents(id);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT chk_fiscal_auto_issue_jobs_attempt_count
        CHECK (attempt_count >= 0);

ALTER TABLE IF EXISTS fiscal_auto_issue_jobs
    ADD CONSTRAINT chk_fiscal_auto_issue_jobs_max_attempts
        CHECK (max_attempts > 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fiscal_auto_issue_jobs_tenant_idempotency
    ON fiscal_auto_issue_jobs (tenant_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_fiscal_auto_issue_jobs_tenant_status
    ON fiscal_auto_issue_jobs (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_fiscal_auto_issue_jobs_status_next_attempt
    ON fiscal_auto_issue_jobs (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_fiscal_auto_issue_jobs_pagamento
    ON fiscal_auto_issue_jobs (tenant_id, pagamento_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_auto_issue_jobs_pedido
    ON fiscal_auto_issue_jobs (tenant_id, pedido_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_auto_issue_jobs_locked_at
    ON fiscal_auto_issue_jobs (locked_at);

