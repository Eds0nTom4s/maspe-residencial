-- ============================================================================
-- V45 — Prompt 41.4: UX Hardening — OwnerActionToken + Lifecycle Job Runs
-- ============================================================================
-- Tabela 1: sessao_owner_action_tokens
--   Token curto emitido após OTP do OWNER para múltiplas ações sem repetir OTP.
-- Tabela 2: sessao_participante_lifecycle_job_runs
--   Observabilidade do job de expiração de participantes.
-- ============================================================================

-- ============================================================================
-- TABELA: sessao_owner_action_tokens
-- ============================================================================
CREATE TABLE IF NOT EXISTS sessao_owner_action_tokens (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT    NOT NULL,
    sessao_consumo_id     BIGINT    NOT NULL,
    owner_participante_id BIGINT    NOT NULL,
    cliente_consumo_id    BIGINT    NOT NULL,
    token_hash            VARCHAR(255) NOT NULL,
    purpose               VARCHAR(80)  NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at           TIMESTAMP WITH TIME ZONE NULL,
    revoked_at            TIMESTAMP WITH TIME ZONE NULL,
    last_used_at          TIMESTAMP WITH TIME ZONE NULL,
    use_count             INTEGER   NOT NULL DEFAULT 0,
    max_uses              INTEGER   NULL,
    client_ip             VARCHAR(100) NULL,
    user_agent            VARCHAR(255) NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NULL,

    -- Foreign keys
    CONSTRAINT fk_owner_action_token_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_owner_action_token_sessao
        FOREIGN KEY (sessao_consumo_id) REFERENCES sessoes_consumo(id),
    CONSTRAINT fk_owner_action_token_participante
        FOREIGN KEY (owner_participante_id) REFERENCES sessao_consumo_participantes(id),
    CONSTRAINT fk_owner_action_token_cliente
        FOREIGN KEY (cliente_consumo_id) REFERENCES cliente_consumo(id),

    -- Constraints
    CONSTRAINT ck_owner_action_token_use_count_nonneg
        CHECK (use_count >= 0),
    CONSTRAINT ck_owner_action_token_status
        CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','CONSUMED'))
);

-- Token hash deve ser único (um token = um hash)
DO $$
BEGIN
    IF to_regclass('public.sessao_owner_action_tokens') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'uk_owner_action_token_hash'
             AND n.nspname = 'public'
             AND t.relname = 'sessao_owner_action_tokens'
       )
    THEN
        ALTER TABLE sessao_owner_action_tokens
            ADD CONSTRAINT uk_owner_action_token_hash UNIQUE (token_hash);
    END IF;
END
$$;

-- Índices operacionais
CREATE INDEX IF NOT EXISTS idx_owner_action_tokens_tenant_sessao
    ON sessao_owner_action_tokens (tenant_id, sessao_consumo_id, status);

CREATE INDEX IF NOT EXISTS idx_owner_action_tokens_owner_status
    ON sessao_owner_action_tokens (tenant_id, owner_participante_id, status, expires_at);

CREATE INDEX IF NOT EXISTS idx_owner_action_tokens_expires_at
    ON sessao_owner_action_tokens (expires_at, status)
    WHERE status = 'ACTIVE';

-- ============================================================================
-- TABELA: sessao_participante_lifecycle_job_runs
-- ============================================================================
CREATE TABLE IF NOT EXISTS sessao_participante_lifecycle_job_runs (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT    NULL,    -- NULL = run global/multi-tenant
    job_name       VARCHAR(100) NOT NULL,
    batch_id       VARCHAR(120) NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at    TIMESTAMP WITH TIME ZONE NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'RUNNING',
    scanned_count  INTEGER   NOT NULL DEFAULT 0,
    expired_count  INTEGER   NOT NULL DEFAULT 0,
    error_message  TEXT      NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_job_run_status
        CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    CONSTRAINT ck_job_run_scanned_nonneg
        CHECK (scanned_count >= 0),
    CONSTRAINT ck_job_run_expired_nonneg
        CHECK (expired_count >= 0)
);

-- batch_id único por job_name
DO $$
BEGIN
    IF to_regclass('public.sessao_participante_lifecycle_job_runs') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'uk_job_run_batch'
             AND n.nspname = 'public'
             AND t.relname = 'sessao_participante_lifecycle_job_runs'
       )
    THEN
        ALTER TABLE sessao_participante_lifecycle_job_runs
            ADD CONSTRAINT uk_job_run_batch UNIQUE (job_name, batch_id);
    END IF;
END
$$;

-- Índice para health check
CREATE INDEX IF NOT EXISTS idx_job_runs_job_name_started
    ON sessao_participante_lifecycle_job_runs (job_name, started_at DESC);

-- Índice para limpeza de runs antigos
CREATE INDEX IF NOT EXISTS idx_job_runs_created_at
    ON sessao_participante_lifecycle_job_runs (created_at);
