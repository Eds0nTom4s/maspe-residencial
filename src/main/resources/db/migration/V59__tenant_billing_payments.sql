-- Prompt 48: Tenant Billing Payments (SaaS invoice payments, overdue/grace, collection policy)

-- Extend invoices with payment + collection fields
alter table if exists tenant_billing_invoices
    add column if not exists total_paid_amount numeric(19,4) not null default 0,
    add column if not exists outstanding_amount numeric(19,4) not null default 0,
    add column if not exists last_payment_at timestamp with time zone null,
    add column if not exists overdue_at timestamp with time zone null,
    add column if not exists grace_period_ends_at timestamp with time zone null,
    add column if not exists collection_status varchar(40) not null default 'CURRENT';

-- Backfill outstanding based on total
update tenant_billing_invoices
set outstanding_amount = greatest(total_amount - total_paid_amount, 0)
where outstanding_amount = 0 and total_amount is not null;

create index if not exists idx_tenant_billing_invoices_collection
    on tenant_billing_invoices(tenant_id, collection_status);

-- Payment sequences (per tenant/year)
create table if not exists tenant_billing_payment_sequences (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    seq_year integer not null,
    last_number bigint not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone null,
    version bigint not null default 0,
    constraint uq_tenant_billing_payment_sequences unique (tenant_id, seq_year)
);

-- Payments
create table if not exists tenant_billing_payments (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    invoice_id bigint not null references tenant_billing_invoices(id),
    billing_cycle_id bigint null references billing_cycles(id),
    subscription_id bigint null references tenant_subscriptions(id),

    payment_number varchar(80) not null,
    status varchar(30) not null,
    payment_method varchar(60) not null,

    amount numeric(19,4) not null,
    currency varchar(3) not null,

    paid_at timestamp with time zone null,
    received_at timestamp with time zone null,
    confirmed_at timestamp with time zone null,

    reference varchar(160) null,
    proof_reference varchar(160) null,
    external_transaction_id varchar(160) null,
    recorded_by_user_id bigint null references users(id),
    notes text null,

    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone null,
    version bigint not null default 0,

    constraint uq_tenant_billing_payments_number unique (tenant_id, payment_number)
);

create index if not exists idx_tenant_billing_payments_invoice
    on tenant_billing_payments(tenant_id, invoice_id, status);

create index if not exists idx_tenant_billing_payments_cycle
    on tenant_billing_payments(tenant_id, billing_cycle_id);

-- Optional payment attempts (placeholder for future gateway integrations)
create table if not exists tenant_billing_payment_attempts (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    invoice_id bigint not null references tenant_billing_invoices(id),
    payment_id bigint null references tenant_billing_payments(id),
    status varchar(30) not null,
    method varchar(60) not null,
    amount numeric(19,4) not null,
    currency varchar(3) not null,
    external_reference varchar(160) null,
    error_code varchar(120) null,
    error_message text null,
    attempted_at timestamp with time zone not null default now(),
    completed_at timestamp with time zone null,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_tenant_billing_payment_attempts_invoice
    on tenant_billing_payment_attempts(tenant_id, invoice_id, attempted_at desc);

-- Collection policy (one per tenant)
create table if not exists tenant_billing_collection_policies (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),

    grace_period_days integer not null default 7,
    overdue_warning_days integer not null default 3,
    auto_mark_overdue boolean not null default true,

    allow_operation_when_overdue boolean not null default true,
    allow_operation_when_suspended boolean not null default false,

    suspension_mode varchar(40) not null default 'WARNING_ONLY',
    suspension_after_days integer not null default 15,

    restrict_new_orders boolean not null default false,
    restrict_new_devices boolean not null default true,
    restrict_admin_access boolean not null default false,

    status varchar(20) not null default 'ACTIVE',

    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone null,
    version bigint not null default 0,

    constraint uq_tenant_billing_collection_policies unique (tenant_id)
);

alter table tenant_billing_payment_attempts add column if not exists updated_at timestamp with time zone null;
alter table tenant_billing_payment_attempts add column if not exists version bigint not null default 0;
alter table tenant_billing_payment_attempts add column if not exists created_by varchar(100);
alter table tenant_billing_payment_attempts add column if not exists modified_by varchar(100);

alter table tenant_billing_collection_policies add column if not exists created_by varchar(100);
alter table tenant_billing_collection_policies add column if not exists modified_by varchar(100);

alter table tenant_billing_payment_sequences add column if not exists created_by varchar(100);
alter table tenant_billing_payment_sequences add column if not exists modified_by varchar(100);

alter table tenant_billing_payments add column if not exists created_by varchar(100);
alter table tenant_billing_payments add column if not exists modified_by varchar(100);
