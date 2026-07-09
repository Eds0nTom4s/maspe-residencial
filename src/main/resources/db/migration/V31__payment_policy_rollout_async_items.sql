-- Prompt 38.4: Rollout assíncrono com progress tracking + rerun seguro

-- 1) Evoluir cabeçalho do rollout para suportar execução assíncrona
alter table payment_method_policy_rollouts
    add column if not exists execution_mode varchar(30) not null default 'SYNC';

alter table payment_method_policy_rollouts
    add column if not exists requested_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists last_progress_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists requested_by bigint null;

alter table payment_method_policy_rollouts
    add column if not exists processed_by varchar(100) null;

alter table payment_method_policy_rollouts
    add column if not exists total_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists processed_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists pending_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists succeeded_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists skipped_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists failed_items integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists retry_count integer not null default 0;

alter table payment_method_policy_rollouts
    add column if not exists last_error text null;

alter table payment_method_policy_rollouts
    add column if not exists locked_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists locked_by varchar(100) null;

alter table payment_method_policy_rollouts
    add column if not exists next_retry_at timestamptz null;

alter table payment_method_policy_rollouts
    add column if not exists idempotency_key varchar(120) null;

-- Para suportar PENDING, started_at precisa ser nullable (era not null no V30)
alter table payment_method_policy_rollouts
    alter column started_at drop not null;

create index if not exists idx_payment_policy_rollouts_tenant_status
    on payment_method_policy_rollouts (tenant_id, status, started_at);

create index if not exists idx_payment_policy_rollouts_lock
    on payment_method_policy_rollouts (locked_at, next_retry_at);

-- UNIQUE (tenant_id, idempotency_key) quando key presente
create unique index if not exists uk_rollout_idempotency_key
    on payment_method_policy_rollouts (tenant_id, idempotency_key)
    where idempotency_key is not null;

-- 2) Itens do rollout (por device + paymentMethod)
create table if not exists payment_method_policy_rollout_items (
    id bigserial primary key,
    tenant_id bigint not null,
    rollout_id bigint not null,
    template_id bigint not null,
    unidade_atendimento_id bigint not null,
    dispositivo_operacional_id bigint not null,
    payment_method_code varchar(50) not null,
    planned_action varchar(50) not null,
    status varchar(30) not null,
    overwrite_mode varchar(50) not null,
    previous_policy_id bigint null,
    resulting_policy_id bigint null,
    template_item_id bigint null,
    manual_override_detected boolean not null default false,
    skipped_reason varchar(100) null,
    error_code varchar(100) null,
    error_message text null,
    attempts integer not null default 0,
    created_at timestamptz not null,
    started_at timestamptz null,
    finished_at timestamptz null,
    updated_at timestamptz null,
    constraint uk_rollout_item unique (tenant_id, rollout_id, dispositivo_operacional_id, payment_method_code)
);

create index if not exists idx_rollout_items_rollout_status
    on payment_method_policy_rollout_items (rollout_id, status, id);

create index if not exists idx_rollout_items_tenant_rollout
    on payment_method_policy_rollout_items (tenant_id, rollout_id, id);

create index if not exists idx_rollout_items_device
    on payment_method_policy_rollout_items (tenant_id, dispositivo_operacional_id, id);

create index if not exists idx_rollout_items_status
    on payment_method_policy_rollout_items (tenant_id, status, id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_tenant
        foreign key (tenant_id) references tenants (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_rollout
        foreign key (rollout_id) references payment_method_policy_rollouts (id) on delete cascade;

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_template
        foreign key (template_id) references payment_method_policy_templates (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_unidade
        foreign key (unidade_atendimento_id) references unidades_atendimento (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_device
        foreign key (dispositivo_operacional_id) references dispositivos_operacionais (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_template_item
        foreign key (template_item_id) references payment_method_policy_template_items (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_previous_policy
        foreign key (previous_policy_id) references device_payment_method_policies (id);

alter table payment_method_policy_rollout_items
    add constraint fk_rollout_items_resulting_policy
        foreign key (resulting_policy_id) references device_payment_method_policies (id);

