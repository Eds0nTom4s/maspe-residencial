-- Prompt 47: Transaction Evidence Ledger (append-only, hash chain per tenant)

create table if not exists transaction_evidence_ledger_states (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    last_sequence bigint not null default 0,
    last_event_hash varchar(128) null,
    last_event_id bigint null,
    last_recorded_at timestamp with time zone null,
    status varchar(30) not null default 'ACTIVE',
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone null,
    version bigint not null default 0,
    constraint uq_transaction_evidence_ledger_states unique (tenant_id)
);

create table if not exists transaction_evidence_events (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    ledger_sequence bigint not null,
    event_type varchar(120) not null,
    source_module varchar(40) not null,
    source_entity_type varchar(80) not null,
    source_entity_id bigint not null,
    source_event_id bigint null,
    occurred_at timestamp with time zone not null,
    recorded_at timestamp with time zone not null default now(),
    idempotency_key varchar(180) not null,
    canonical_payload_version varchar(40) not null,
    canonical_payload_json text not null,
    canonical_payload_hash varchar(128) not null,
    previous_event_hash varchar(128) not null,
    event_hash varchar(128) not null,
    hmac_signature varchar(256) not null,
    key_version varchar(40) not null,
    algorithm varchar(40) not null,
    status varchar(30) not null default 'RECORDED',
    verification_status varchar(40) not null default 'NOT_VERIFIED',
    payload_summary_json text null,
    metadata_json text null,
    created_at timestamp with time zone not null default now(),
    version bigint not null default 0,
    constraint uq_transaction_evidence_events_seq unique (tenant_id, ledger_sequence),
    constraint uq_transaction_evidence_events_idem unique (tenant_id, idempotency_key)
);

create index if not exists idx_transaction_evidence_events_type_time
    on transaction_evidence_events(tenant_id, event_type, occurred_at);

create index if not exists idx_transaction_evidence_events_source
    on transaction_evidence_events(tenant_id, source_entity_type, source_entity_id);

create table if not exists transaction_evidence_verification_runs (
    id bigserial primary key,
    tenant_id bigint not null references tenants(id),
    period_start timestamp with time zone not null,
    period_end timestamp with time zone not null,
    status varchar(30) not null,
    checked_events_count integer not null default 0,
    invalid_events_count integer not null default 0,
    broken_chain_count integer not null default 0,
    sequence_gap_count integer not null default 0,
    started_at timestamp with time zone not null default now(),
    finished_at timestamp with time zone null,
    report_hash varchar(128) null,
    created_by_user_id bigint null references users(id),
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_transaction_evidence_verification_runs
    on transaction_evidence_verification_runs(tenant_id, started_at desc);

create table if not exists transaction_evidence_verification_issues (
    id bigserial primary key,
    verification_run_id bigint not null references transaction_evidence_verification_runs(id),
    tenant_id bigint not null references tenants(id),
    event_id bigint null references transaction_evidence_events(id),
    ledger_sequence bigint null,
    issue_type varchar(40) not null,
    description text not null,
    detected_at timestamp with time zone not null default now(),
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_transaction_evidence_verification_issues
    on transaction_evidence_verification_issues(tenant_id, verification_run_id, issue_type);

