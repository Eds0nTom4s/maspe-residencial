-- Prompt 45: Preparação para faturação eletrónica (AGT-ready) - estrutura interna desativada por padrão

CREATE TABLE IF NOT EXISTS tenant_official_fiscal_profiles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,

    status VARCHAR(40) NOT NULL DEFAULT 'NOT_CONFIGURED',
    country_code VARCHAR(10) NOT NULL DEFAULT 'AO',
    authority VARCHAR(40) NOT NULL DEFAULT 'AGT_AO',

    official_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    environment VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
    submission_mode VARCHAR(80) NOT NULL DEFAULT 'DISABLED',

    taxpayer_number VARCHAR(80) NULL,
    software_certificate_id VARCHAR(120) NULL,
    software_name VARCHAR(120) NULL,
    software_version VARCHAR(60) NULL,
    producer_registration_id VARCHAR(120) NULL,

    public_key_id VARCHAR(120) NULL,
    taxpayer_key_id VARCHAR(120) NULL,
    signing_profile_id BIGINT NULL,

    callback_url VARCHAR(255) NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_tofp_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_tofp_tenant UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tofp_tenant_status
    ON tenant_official_fiscal_profiles (tenant_id, status);

-- Signing profiles (não armazenar chave privada)
CREATE TABLE IF NOT EXISTS fiscal_signing_profiles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,

    status VARCHAR(40) NOT NULL DEFAULT 'INACTIVE',
    key_provider VARCHAR(60) NOT NULL DEFAULT 'MANUAL_PLACEHOLDER',
    key_alias VARCHAR(120) NULL,
    public_key_fingerprint VARCHAR(160) NULL,
    algorithm VARCHAR(40) NOT NULL DEFAULT 'RS256',
    key_size INTEGER NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    rotated_at TIMESTAMP WITH TIME ZONE NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_fsp_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_fsp_tenant_status
    ON fiscal_signing_profiles (tenant_id, status);

-- Official submissions (processo oficial futuro)
CREATE TABLE IF NOT EXISTS official_fiscal_submissions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    fiscal_document_id BIGINT NOT NULL,
    original_fiscal_document_id BIGINT NULL,

    document_type VARCHAR(80) NOT NULL,

    status VARCHAR(60) NOT NULL DEFAULT 'DRAFT',
    authority VARCHAR(40) NOT NULL DEFAULT 'AGT_AO',
    environment VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',

    request_id VARCHAR(160) NULL,
    idempotency_key VARCHAR(200) NOT NULL,

    official_document_id VARCHAR(160) NULL,
    official_status_code VARCHAR(80) NULL,
    official_status_message TEXT NULL,

    payload_hash VARCHAR(64) NULL,
    signed_payload_hash VARCHAR(64) NULL,
    jws_document_signature_hash VARCHAR(64) NULL,
    jws_request_signature_hash VARCHAR(64) NULL,

    submitted_at TIMESTAMP WITH TIME ZONE NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NULL,

    last_attempt_at TIMESTAMP WITH TIME ZONE NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,

    locked_at TIMESTAMP WITH TIME ZONE NULL,
    locked_by VARCHAR(120) NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ofs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_ofs_fiscal_document FOREIGN KEY (fiscal_document_id) REFERENCES fiscal_documents(id),
    CONSTRAINT fk_ofs_original_fiscal_document FOREIGN KEY (original_fiscal_document_id) REFERENCES fiscal_documents(id),
    CONSTRAINT uq_ofs_tenant_document UNIQUE (tenant_id, fiscal_document_id),
    CONSTRAINT uq_ofs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_ofs_attempt_count_nonneg CHECK (attempt_count >= 0),
    CONSTRAINT chk_ofs_max_attempts_pos CHECK (max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofs_tenant_status
    ON official_fiscal_submissions (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_ofs_status_next_attempt
    ON official_fiscal_submissions (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_ofs_request_id
    ON official_fiscal_submissions (tenant_id, request_id);

ALTER TABLE tenant_official_fiscal_profiles
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE tenant_official_fiscal_profiles
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

ALTER TABLE fiscal_signing_profiles
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE fiscal_signing_profiles
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

ALTER TABLE official_fiscal_submissions
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE official_fiscal_submissions
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_ofs_locked_at
    ON official_fiscal_submissions (status, locked_at);

-- Attempts
CREATE TABLE IF NOT EXISTS official_fiscal_submission_attempts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    official_fiscal_submission_id BIGINT NOT NULL,

    attempt_number INTEGER NOT NULL,
    status VARCHAR(60) NOT NULL,
    request_id VARCHAR(160) NULL,
    http_status INTEGER NULL,
    error_code VARCHAR(120) NULL,
    error_message TEXT NULL,

    request_payload_hash VARCHAR(64) NULL,
    response_payload_hash VARCHAR(64) NULL,

    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ofsa_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_ofsa_submission FOREIGN KEY (official_fiscal_submission_id) REFERENCES official_fiscal_submissions(id),
    CONSTRAINT chk_ofsa_attempt_number_pos CHECK (attempt_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofsa_tenant_submission
    ON official_fiscal_submission_attempts (tenant_id, official_fiscal_submission_id);

CREATE INDEX IF NOT EXISTS idx_ofsa_tenant_request
    ON official_fiscal_submission_attempts (tenant_id, request_id);

ALTER TABLE official_fiscal_submission_attempts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE official_fiscal_submission_attempts
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE official_fiscal_submission_attempts
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);
ALTER TABLE official_fiscal_submission_attempts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
