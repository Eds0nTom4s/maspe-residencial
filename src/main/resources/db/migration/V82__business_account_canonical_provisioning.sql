CREATE TABLE business_account_governance_events (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    business_account_id BIGINT,
    scope_key VARCHAR(120) NOT NULL,
    action VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    actor_user_id BIGINT,
    actor_roles VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    before_state TEXT,
    after_state TEXT,
    result_account_id BIGINT,
    result_member_id BIGINT,
    CONSTRAINT fk_ba_governance_account FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_ba_governance_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_ba_governance_result_account FOREIGN KEY (result_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_ba_governance_result_member FOREIGN KEY (result_member_id) REFERENCES business_account_members(id),
    CONSTRAINT uq_ba_governance_idempotency UNIQUE (scope_key, action, idempotency_key)
);

CREATE INDEX idx_ba_governance_account_created
    ON business_account_governance_events(business_account_id, created_at DESC);

CREATE TABLE business_provisioning_previews (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    preview_id VARCHAR(36) NOT NULL,
    business_account_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    contract_version VARCHAR(40) NOT NULL,
    template_code VARCHAR(60) NOT NULL,
    template_version INTEGER NOT NULL,
    plano_codigo VARCHAR(30) NOT NULL,
    payload_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    correlation_id VARCHAR(120) NOT NULL,
    actor_roles VARCHAR(500) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT fk_business_preview_account FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_business_preview_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT uq_business_preview_public_id UNIQUE (preview_id),
    CONSTRAINT uq_business_preview_idempotency UNIQUE (business_account_id, idempotency_key)
);

CREATE INDEX idx_business_preview_fingerprint
    ON business_provisioning_previews(business_account_id, request_fingerprint, expires_at DESC);

CREATE TABLE business_provisioning_operations (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    operation_id VARCHAR(36) NOT NULL,
    business_account_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    preview_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    tenant_id BIGINT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_code VARCHAR(120),
    error_message VARCHAR(500),
    result_json TEXT,
    correlation_id VARCHAR(120) NOT NULL,
    actor_roles VARCHAR(500) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT fk_business_operation_account FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_business_operation_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_business_operation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_business_operation_preview FOREIGN KEY (preview_id) REFERENCES business_provisioning_previews(preview_id),
    CONSTRAINT uq_business_operation_public_id UNIQUE (operation_id),
    CONSTRAINT uq_business_operation_idempotency UNIQUE (business_account_id, idempotency_key)
);

CREATE INDEX idx_business_operation_account_created
    ON business_provisioning_operations(business_account_id, created_at DESC);

CREATE TABLE legacy_provisioning_usage_events (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    endpoint VARCHAR(180) NOT NULL,
    actor_user_id BIGINT,
    actor_roles VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT fk_legacy_provisioning_usage_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_legacy_provisioning_usage_created
    ON legacy_provisioning_usage_events(endpoint, created_at DESC);

-- Os dados legados auditados ainda contêm contas sem owner, múltiplos OWNER e
-- tenants sem BusinessAccount. Por isso esta migration não adiciona NOT NULL em
-- tenants.business_account_id nem unique parcial de OWNER. O fluxo canónico
-- garante os invariantes em transacção; o backfill será posterior e auditado.
