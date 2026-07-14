ALTER TABLE pagamento_reconciliation_case_events ADD COLUMN command_fingerprint VARCHAR(64);
ALTER TABLE pagamento_reconciliation_case_events ADD CONSTRAINT fk_reconciliation_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE pagamento_reconciliation_case_events ADD CONSTRAINT fk_reconciliation_event_payment FOREIGN KEY (pagamento_id) REFERENCES pagamentos_gateway(id);
ALTER TABLE pagamento_reconciliation_case_events ADD CONSTRAINT fk_reconciliation_event_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id);
ALTER TABLE pagamento_reconciliation_case_events ADD CONSTRAINT fk_reconciliation_event_actor FOREIGN KEY (actor_user_id) REFERENCES users(id);
CREATE TABLE reconciliation_materialization_audits (
 id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, tenant_id BIGINT NOT NULL REFERENCES tenants(id),
 actor_user_id BIGINT NOT NULL REFERENCES users(id), actor_roles VARCHAR(500) NOT NULL, actor_origin VARCHAR(50) NOT NULL,
 correlation_id VARCHAR(100) NOT NULL, idempotency_key VARCHAR(100) NOT NULL UNIQUE, command_fingerprint VARCHAR(64) NOT NULL,
 reason VARCHAR(1000) NOT NULL, dry_run BOOLEAN NOT NULL, eligible_count BIGINT NOT NULL, created_count BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(100), modified_by VARCHAR(100)
);
CREATE INDEX idx_reconciliation_materialization_tenant_created ON reconciliation_materialization_audits(tenant_id,created_at);
