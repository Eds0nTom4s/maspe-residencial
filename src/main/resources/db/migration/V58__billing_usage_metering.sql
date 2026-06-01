-- Prompt 46 — Usage Metering e Cobrança por Transação (Billing Core)

-- =========================================================
-- Billing plans (plataforma)
-- =========================================================
CREATE TABLE IF NOT EXISTS billing_plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    billing_interval VARCHAR(30) NOT NULL DEFAULT 'MONTHLY',
    currency VARCHAR(3) NOT NULL DEFAULT 'AOA',
    base_price NUMERIC(19, 4) NOT NULL DEFAULT 0,
    included_transactions BIGINT NOT NULL DEFAULT 0,
    included_devices BIGINT NOT NULL DEFAULT 0,
    included_units BIGINT NOT NULL DEFAULT 0,
    overage_price_per_transaction NUMERIC(19, 4) NOT NULL DEFAULT 0,
    overage_price_per_device NUMERIC(19, 4) NOT NULL DEFAULT 0,
    overage_price_per_unit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    transaction_fee_percentage NUMERIC(9, 6) NOT NULL DEFAULT 0,
    minimum_monthly_fee NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_plans_code ON billing_plans (code);
CREATE INDEX IF NOT EXISTS idx_billing_plans_status ON billing_plans (status);

-- =========================================================
-- Usage metrics (plataforma)
-- =========================================================
CREATE TABLE IF NOT EXISTS usage_metrics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    unit VARCHAR(40) NOT NULL DEFAULT 'COUNT',
    aggregation_type VARCHAR(40) NOT NULL DEFAULT 'COUNT',
    billable BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_metrics_code ON usage_metrics (code);
CREATE INDEX IF NOT EXISTS idx_usage_metrics_billable ON usage_metrics (billable);

-- Seed (idempotente): métricas base
INSERT INTO usage_metrics (code, name, description, unit, aggregation_type, billable, status)
VALUES
    ('PAYMENT_CONFIRMED', 'Payment confirmed', 'Pagamento confirmado (métrica principal para cobrança SaaS)', 'COUNT', 'COUNT', true, 'ACTIVE'),
    ('PAID_ORDER', 'Paid order', 'Pedido pago (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('APPYPAY_PAYMENT_CONFIRMED', 'AppyPay payment confirmed', 'Pagamento AppyPay confirmado (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('MANUAL_PAYMENT_CONFIRMED', 'Manual payment confirmed', 'Pagamento manual CASH/TPA confirmado (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('FISCAL_DOCUMENT_ISSUED', 'Fiscal document issued', 'Documento fiscal interno emitido (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('OFFICIAL_FISCAL_SUBMISSION_CREATED', 'Official submission created', 'Submissão oficial placeholder criada (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('ACTIVE_DEVICE_DAY', 'Active device day', 'Dispositivos ativos por dia (tracked)', 'COUNT', 'DAILY_ACTIVE_COUNT', false, 'ACTIVE'),
    ('ACTIVE_UNIT_DAY', 'Active unit day', 'Unidades ativas por dia (tracked)', 'COUNT', 'DAILY_ACTIVE_COUNT', false, 'ACTIVE'),
    ('INVENTORY_CONSUMPTION_PROCESSED', 'Inventory consumption processed', 'Consumo de inventário processado (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE'),
    ('RETURN_PROCESSED', 'Return processed', 'Devolução processada (tracked)', 'COUNT', 'COUNT', false, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- =========================================================
-- Tenant subscription
-- =========================================================
CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    billing_plan_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'TRIALING',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NULL,
    trial_ends_at TIMESTAMP WITH TIME ZONE NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'AOA',
    billing_anchor_day INTEGER NOT NULL DEFAULT 1,
    auto_renew BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.tenant_subscriptions') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_subscriptions_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_subscriptions'
       )
    THEN
        ALTER TABLE tenant_subscriptions
            ADD CONSTRAINT fk_tenant_subscriptions_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.tenant_subscriptions') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_subscriptions_plan'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_subscriptions'
       )
    THEN
        ALTER TABLE tenant_subscriptions
            ADD CONSTRAINT fk_tenant_subscriptions_plan
                FOREIGN KEY (billing_plan_id) REFERENCES billing_plans (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_tenant_status ON tenant_subscriptions (tenant_id, status);

-- =========================================================
-- Billing cycles
-- =========================================================
CREATE TABLE IF NOT EXISTS billing_cycles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    usage_finalized_at TIMESTAMP WITH TIME ZONE NULL,
    invoice_generated_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.billing_cycles') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_billing_cycles_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'billing_cycles'
       )
    THEN
        ALTER TABLE billing_cycles
            ADD CONSTRAINT fk_billing_cycles_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.billing_cycles') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_billing_cycles_subscription'
             AND n.nspname = 'public'
             AND t.relname = 'billing_cycles'
       )
    THEN
        ALTER TABLE billing_cycles
            ADD CONSTRAINT fk_billing_cycles_subscription
                FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_cycles_unique_period ON billing_cycles (tenant_id, subscription_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_billing_cycles_tenant_status ON billing_cycles (tenant_id, status);

-- =========================================================
-- Usage events (append-only + idempotente)
-- =========================================================
CREATE TABLE IF NOT EXISTS usage_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    metric_code VARCHAR(80) NOT NULL,
    source_event_type VARCHAR(120) NULL,
    source_entity_type VARCHAR(120) NULL,
    source_entity_id BIGINT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL DEFAULT 1,
    amount NUMERIC(19, 4) NULL,
    currency VARCHAR(3) NULL,
    unidade_id BIGINT NULL,
    operational_device_id BIGINT NULL,
    metadata_json JSONB NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECORDED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.usage_events') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_events_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'usage_events'
       )
    THEN
        ALTER TABLE usage_events
            ADD CONSTRAINT fk_usage_events_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_events_idempotency ON usage_events (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_usage_events_tenant_metric_time ON usage_events (tenant_id, metric_code, occurred_at);
CREATE INDEX IF NOT EXISTS idx_usage_events_source_entity ON usage_events (tenant_id, source_entity_type, source_entity_id);

-- =========================================================
-- Usage adjustments (preparatório)
-- =========================================================
CREATE TABLE IF NOT EXISTS usage_adjustments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    original_usage_event_id BIGINT NULL,
    adjustment_type VARCHAR(40) NOT NULL,
    metric_code VARCHAR(80) NOT NULL,
    quantity_delta NUMERIC(19, 6) NOT NULL DEFAULT 0,
    amount_delta NUMERIC(19, 4) NOT NULL DEFAULT 0,
    reason TEXT NULL,
    reference_type VARCHAR(120) NULL,
    reference_id BIGINT NULL,
    created_by_user_id BIGINT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.usage_adjustments') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_adjustments_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'usage_adjustments'
       )
    THEN
        ALTER TABLE usage_adjustments
            ADD CONSTRAINT fk_usage_adjustments_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.usage_adjustments') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_adjustments_original_event'
             AND n.nspname = 'public'
             AND t.relname = 'usage_adjustments'
       )
    THEN
        ALTER TABLE usage_adjustments
            ADD CONSTRAINT fk_usage_adjustments_original_event
                FOREIGN KEY (original_usage_event_id) REFERENCES usage_events (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_usage_adjustments_tenant_created_at ON usage_adjustments (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_usage_adjustments_tenant_metric ON usage_adjustments (tenant_id, metric_code);

-- =========================================================
-- Usage aggregations
-- =========================================================
CREATE TABLE IF NOT EXISTS usage_aggregations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    billing_cycle_id BIGINT NULL,
    metric_code VARCHAR(80) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    quantity_total NUMERIC(19, 6) NOT NULL DEFAULT 0,
    amount_total NUMERIC(19, 4) NOT NULL DEFAULT 0,
    billable_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    included_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    overage_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    calculated_charge_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'AOA',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.usage_aggregations') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_aggregations_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'usage_aggregations'
       )
    THEN
        ALTER TABLE usage_aggregations
            ADD CONSTRAINT fk_usage_aggregations_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.usage_aggregations') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_aggregations_subscription'
             AND n.nspname = 'public'
             AND t.relname = 'usage_aggregations'
       )
    THEN
        ALTER TABLE usage_aggregations
            ADD CONSTRAINT fk_usage_aggregations_subscription
                FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.usage_aggregations') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_usage_aggregations_cycle'
             AND n.nspname = 'public'
             AND t.relname = 'usage_aggregations'
       )
    THEN
        ALTER TABLE usage_aggregations
            ADD CONSTRAINT fk_usage_aggregations_cycle
                FOREIGN KEY (billing_cycle_id) REFERENCES billing_cycles (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_usage_aggregations_tenant_metric_period ON usage_aggregations (tenant_id, metric_code, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_usage_aggregations_tenant_cycle ON usage_aggregations (tenant_id, billing_cycle_id);

-- =========================================================
-- Invoice sequence (por tenant/ano)
-- =========================================================
CREATE TABLE IF NOT EXISTS tenant_billing_invoice_sequences (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    seq_year INTEGER NOT NULL,
    current_number BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoice_sequences') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoice_sequences_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoice_sequences'
       )
    THEN
        ALTER TABLE tenant_billing_invoice_sequences
            ADD CONSTRAINT fk_tenant_billing_invoice_sequences_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_billing_invoice_sequences_key ON tenant_billing_invoice_sequences (tenant_id, seq_year);

-- =========================================================
-- Tenant billing invoices (internas)
-- =========================================================
CREATE TABLE IF NOT EXISTS tenant_billing_invoices (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    billing_cycle_id BIGINT NOT NULL,
    invoice_number VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3) NOT NULL DEFAULT 'AOA',
    subtotal_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    issued_at TIMESTAMP WITH TIME ZONE NULL,
    due_at TIMESTAMP WITH TIME ZONE NULL,
    paid_at TIMESTAMP WITH TIME ZONE NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NULL,
    notes TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoices') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoices_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoices'
       )
    THEN
        ALTER TABLE tenant_billing_invoices
            ADD CONSTRAINT fk_tenant_billing_invoices_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoices') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoices_subscription'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoices'
       )
    THEN
        ALTER TABLE tenant_billing_invoices
            ADD CONSTRAINT fk_tenant_billing_invoices_subscription
                FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoices') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoices_cycle'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoices'
       )
    THEN
        ALTER TABLE tenant_billing_invoices
            ADD CONSTRAINT fk_tenant_billing_invoices_cycle
                FOREIGN KEY (billing_cycle_id) REFERENCES billing_cycles (id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_billing_invoices_cycle ON tenant_billing_invoices (tenant_id, billing_cycle_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_billing_invoices_number ON tenant_billing_invoices (tenant_id, invoice_number);
CREATE INDEX IF NOT EXISTS idx_tenant_billing_invoices_status ON tenant_billing_invoices (tenant_id, status);

CREATE TABLE IF NOT EXISTS tenant_billing_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    tenant_billing_invoice_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    metric_code VARCHAR(80) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    unit_price NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    included_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    overage_quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoice_lines') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoice_lines_invoice'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoice_lines'
       )
    THEN
        ALTER TABLE tenant_billing_invoice_lines
            ADD CONSTRAINT fk_tenant_billing_invoice_lines_invoice
                FOREIGN KEY (tenant_billing_invoice_id) REFERENCES tenant_billing_invoices (id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.tenant_billing_invoice_lines') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint c
           JOIN pg_class t ON t.oid = c.conrelid
           JOIN pg_namespace n ON n.oid = t.relnamespace
           WHERE c.conname = 'fk_tenant_billing_invoice_lines_tenant'
             AND n.nspname = 'public'
             AND t.relname = 'tenant_billing_invoice_lines'
       )
    THEN
        ALTER TABLE tenant_billing_invoice_lines
            ADD CONSTRAINT fk_tenant_billing_invoice_lines_tenant
                FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_tenant_billing_invoice_lines_invoice ON tenant_billing_invoice_lines (tenant_billing_invoice_id);
