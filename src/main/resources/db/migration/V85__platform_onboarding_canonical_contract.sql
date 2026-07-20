-- Contrato canónico V2. Rows preexistentes permanecem com contract_version NULL
-- e nenhum estado legacy é reescrito ou inferido como CONCLUIDO.
ALTER TABLE onboarding_requests
    ADD COLUMN contract_version VARCHAR(50),
    ADD COLUMN channel VARCHAR(40),
    ADD COLUMN normalized_nif VARCHAR(30),
    ADD COLUMN nif_resolution VARCHAR(40),
    ADD COLUMN nif_candidate_business_account_id BIGINT,
    ADD COLUMN confirmed_plan_id BIGINT,
    ADD COLUMN vertical VARCHAR(30),
    ADD COLUMN account_choice VARCHAR(30),
    ADD COLUMN owner_strategy VARCHAR(40),
    ADD COLUMN owner_result_user_id BIGINT,
    ADD COLUMN provisioning_operation_id BIGINT,
    ADD COLUMN approval_reason VARCHAR(500),
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN cancelled_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP,
    ADD CONSTRAINT fk_onboarding_nif_candidate_account
        FOREIGN KEY (nif_candidate_business_account_id) REFERENCES business_accounts(id),
    ADD CONSTRAINT fk_onboarding_confirmed_plan
        FOREIGN KEY (confirmed_plan_id) REFERENCES planos(id),
    ADD CONSTRAINT fk_onboarding_owner_result_user
        FOREIGN KEY (owner_result_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_onboarding_provisioning_operation
        FOREIGN KEY (provisioning_operation_id) REFERENCES business_provisioning_operations(id);

CREATE INDEX idx_onboarding_normalized_nif
    ON onboarding_requests(normalized_nif)
    WHERE contract_version = 'ONBOARDING_CANONICAL_V2';

CREATE UNIQUE INDEX uq_onboarding_provisioning_operation
    ON onboarding_requests(provisioning_operation_id)
    WHERE provisioning_operation_id IS NOT NULL;

CREATE TABLE onboarding_nif_reservations (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    normalized_nif VARCHAR(30) NOT NULL,
    onboarding_request_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    business_account_id BIGINT,
    CONSTRAINT fk_onboarding_nif_reservation_request
        FOREIGN KEY (onboarding_request_id) REFERENCES onboarding_requests(id),
    CONSTRAINT fk_onboarding_nif_reservation_account
        FOREIGN KEY (business_account_id) REFERENCES business_accounts(id),
    CONSTRAINT uq_onboarding_nif_reservation_request UNIQUE (onboarding_request_id),
    CONSTRAINT ck_onboarding_nif_reservation_state
        CHECK (state IN ('ACTIVE', 'CONSUMED', 'RELEASED'))
);

-- Só a intenção concorrente de criar Account é exclusiva. CONSUMED/RELEASED
-- não bloqueiam onboardings posteriores para novos Negócios da mesma empresa.
CREATE UNIQUE INDEX uq_onboarding_nif_reservation_active
    ON onboarding_nif_reservations(normalized_nif)
    WHERE state = 'ACTIVE';

CREATE TABLE onboarding_command_records (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    scope_key VARCHAR(120) NOT NULL,
    action VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    onboarding_request_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    actor_roles VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    before_state TEXT,
    after_state TEXT,
    reason VARCHAR(500),
    result_json TEXT NOT NULL,
    result_account_id BIGINT,
    result_operation_id VARCHAR(36),
    result_tenant_id BIGINT,
    CONSTRAINT fk_onboarding_command_request
        FOREIGN KEY (onboarding_request_id) REFERENCES onboarding_requests(id),
    CONSTRAINT fk_onboarding_command_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_onboarding_command_result_account
        FOREIGN KEY (result_account_id) REFERENCES business_accounts(id),
    CONSTRAINT fk_onboarding_command_result_operation
        FOREIGN KEY (result_operation_id) REFERENCES business_provisioning_operations(operation_id),
    CONSTRAINT fk_onboarding_command_result_tenant
        FOREIGN KEY (result_tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_onboarding_command_idempotency
        UNIQUE (scope_key, action, idempotency_key)
);

CREATE INDEX idx_onboarding_command_request_created
    ON onboarding_command_records(onboarding_request_id, created_at DESC);
