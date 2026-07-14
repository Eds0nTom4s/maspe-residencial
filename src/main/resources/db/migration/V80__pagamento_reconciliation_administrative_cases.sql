CREATE TABLE pagamento_reconciliation_cases (
    id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    pagamento_gateway_id BIGINT NOT NULL REFERENCES pagamentos_gateway(id),
    pedido_id BIGINT REFERENCES pedidos(id), status VARCHAR(50) NOT NULL,
    classification VARCHAR(60), assigned_to_user_id BIGINT REFERENCES users(id),
    opened_at TIMESTAMP NOT NULL, opened_by VARCHAR(100) NOT NULL,
    resolved_at TIMESTAMP, resolved_by VARCHAR(100), resolution VARCHAR(60),
    resolution_reason VARCHAR(1000), created_from_reconciliation_status VARCHAR(50) NOT NULL,
    origin VARCHAR(30) NOT NULL, remote_status_snapshot VARCHAR(50),
    local_status_snapshot VARCHAR(50), remote_reference_snapshot VARCHAR(100),
    response_fingerprint_snapshot VARCHAR(64), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP,
    created_by VARCHAR(100), modified_by VARCHAR(100)
);
CREATE UNIQUE INDEX ux_reconciliation_case_active_payment ON pagamento_reconciliation_cases(pagamento_gateway_id) WHERE active;
CREATE INDEX idx_reconciliation_case_tenant_status_updated ON pagamento_reconciliation_cases(tenant_id,status,updated_at DESC);
CREATE INDEX idx_reconciliation_case_assignee_status ON pagamento_reconciliation_cases(assigned_to_user_id,status);
CREATE INDEX idx_reconciliation_case_classification_status ON pagamento_reconciliation_cases(classification,status);

CREATE TABLE pagamento_reconciliation_case_events (
    id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
    case_id BIGINT NOT NULL REFERENCES pagamento_reconciliation_cases(id), tenant_id BIGINT NOT NULL,
    pagamento_id BIGINT NOT NULL, pedido_id BIGINT, actor_user_id BIGINT,
    actor_roles VARCHAR(500) NOT NULL, actor_origin VARCHAR(50) NOT NULL,
    ip VARCHAR(64), user_agent VARCHAR(255), correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100), action VARCHAR(40) NOT NULL,
    before_state TEXT, after_state TEXT, reason VARCHAR(1000) NOT NULL,
    note_type VARCHAR(30), note_visibility VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP,
    created_by VARCHAR(100), modified_by VARCHAR(100)
);
CREATE INDEX idx_reconciliation_event_case_created ON pagamento_reconciliation_case_events(case_id,created_at);
CREATE UNIQUE INDEX ux_reconciliation_event_idempotency ON pagamento_reconciliation_case_events(case_id,action,idempotency_key) WHERE idempotency_key IS NOT NULL;
