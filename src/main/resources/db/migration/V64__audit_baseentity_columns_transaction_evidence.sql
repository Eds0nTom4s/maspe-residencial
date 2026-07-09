-- Ensure BaseEntity audit/version columns exist for transaction_evidence_* tables
-- Required for Hibernate schema validation (BaseEntity fields).

-- transaction_evidence_events: missing created_by/modified_by/updated_at
alter table if exists transaction_evidence_events
    add column if not exists updated_at timestamp with time zone null;
alter table if exists transaction_evidence_events
    add column if not exists created_by varchar(100);
alter table if exists transaction_evidence_events
    add column if not exists modified_by varchar(100);

-- transaction_evidence_ledger_states: missing created_by/modified_by
alter table if exists transaction_evidence_ledger_states
    add column if not exists created_by varchar(100);
alter table if exists transaction_evidence_ledger_states
    add column if not exists modified_by varchar(100);

-- transaction_evidence_verification_runs: missing updated_at/created_by/modified_by/version
alter table if exists transaction_evidence_verification_runs
    add column if not exists updated_at timestamp with time zone null;
alter table if exists transaction_evidence_verification_runs
    add column if not exists created_by varchar(100);
alter table if exists transaction_evidence_verification_runs
    add column if not exists modified_by varchar(100);
alter table if exists transaction_evidence_verification_runs
    add column if not exists version bigint not null default 0;

-- transaction_evidence_verification_issues: missing updated_at/created_by/modified_by/version
alter table if exists transaction_evidence_verification_issues
    add column if not exists updated_at timestamp with time zone null;
alter table if exists transaction_evidence_verification_issues
    add column if not exists created_by varchar(100);
alter table if exists transaction_evidence_verification_issues
    add column if not exists modified_by varchar(100);
alter table if exists transaction_evidence_verification_issues
    add column if not exists version bigint not null default 0;

