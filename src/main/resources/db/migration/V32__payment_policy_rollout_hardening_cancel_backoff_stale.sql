-- Prompt 38.5: Hardening do rollout async (cancelamento + backoff + stale recovery + rate-limit audit)

-- 1) Rollout header hardening
alter table payment_method_policy_rollouts
    add column if not exists cancel_requested boolean not null default false;

alter table payment_method_policy_rollouts
    add column if not exists cancel_requested_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists cancel_requested_by bigint null;

alter table payment_method_policy_rollouts
    add column if not exists cancelled_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists cancellation_reason varchar(255) null;

alter table payment_method_policy_rollouts
    add column if not exists last_progress_event_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists last_progress_event_percent integer null;

alter table payment_method_policy_rollouts
    add column if not exists stale_recovery_count integer not null default 0;

create index if not exists idx_payment_policy_rollouts_cancel
    on payment_method_policy_rollouts (tenant_id, status, cancel_requested);

-- 2) Rollout item hardening
alter table payment_method_policy_rollout_items
    add column if not exists next_retry_at timestamptz null;

alter table payment_method_policy_rollout_items
    add column if not exists last_attempt_at timestamptz null;

alter table payment_method_policy_rollout_items
    add column if not exists last_locked_at timestamptz null;

alter table payment_method_policy_rollout_items
    add column if not exists locked_by varchar(100) null;

alter table payment_method_policy_rollout_items
    add column if not exists stale_recovery_count integer not null default 0;

alter table payment_method_policy_rollout_items
    add column if not exists terminal_failure boolean not null default false;

create index if not exists idx_rollout_items_retry
    on payment_method_policy_rollout_items (tenant_id, rollout_id, status, next_retry_at, id);

create index if not exists idx_rollout_items_lock
    on payment_method_policy_rollout_items (tenant_id, status, last_locked_at, id);

