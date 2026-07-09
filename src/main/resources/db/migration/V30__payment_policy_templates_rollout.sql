-- Prompt 38.3: Templates de policy por tipo de device + rollout por unidade

-- 1) Device operational type (mais granular que DispositivoTipo)
alter table dispositivos_operacionais
    add column if not exists operational_device_type varchar(50) not null default 'GENERIC_DEVICE';

-- Best-effort backfill baseado no tipo atual
update dispositivos_operacionais
set operational_device_type = case tipo
    when 'CHECKOUT' then 'POS_CAIXA'
    when 'POS' then 'POS_ATENDIMENTO'
    when 'QUIOSQUE' then 'POS_QUIOSQUE'
    when 'KDS' then 'KDS_COZINHA'
    when 'COZINHA' then 'KDS_COZINHA'
    when 'BAR' then 'KDS_BAR'
    when 'ADMIN' then 'ADMIN_TERMINAL'
    else 'GENERIC_DEVICE'
end
where operational_device_type = 'GENERIC_DEVICE';

create index if not exists idx_dispositivo_tenant_unidade_operational_type
    on dispositivos_operacionais (tenant_id, unidade_atendimento_id, operational_device_type);

-- 2) Campos template-managed em device policies
alter table device_payment_method_policies
    add column if not exists source_template_id bigint null;

alter table device_payment_method_policies
    add column if not exists source_rollout_id bigint null;

alter table device_payment_method_policies
    add column if not exists template_managed boolean not null default false;

alter table device_payment_method_policies
    add column if not exists manual_override boolean not null default false;

alter table device_payment_method_policies
    add column if not exists template_applied_at timestamptz null;

create index if not exists idx_device_pmp_template_managed
    on device_payment_method_policies (tenant_id, template_managed, manual_override);

-- 3) Templates
create table if not exists payment_method_policy_templates (
    id bigserial primary key,
    tenant_id bigint not null,
    code varchar(80) not null,
    name varchar(120) not null,
    description varchar(255) null,
    target_device_type varchar(50) null,
    status varchar(30) not null,
    is_system_default boolean not null default false,
    version integer not null default 1,
    metadata_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz null,
    created_by bigint null,
    updated_by bigint null,
    constraint uk_payment_policy_template unique (tenant_id, code)
);

create index if not exists idx_payment_policy_templates_tenant on payment_method_policy_templates (tenant_id);
create index if not exists idx_payment_policy_templates_tenant_status on payment_method_policy_templates (tenant_id, status);

alter table payment_method_policy_templates
    add constraint fk_payment_policy_templates_tenant
        foreign key (tenant_id) references tenants (id);

create table if not exists payment_method_policy_template_items (
    id bigserial primary key,
    tenant_id bigint not null,
    template_id bigint not null,
    payment_method_code varchar(50) not null,
    policy_status varchar(30) not null,
    enabled_for_pos boolean null,
    enabled_for_pedido boolean null,
    enabled_for_fundo_consumo boolean null,
    can_confirm_manual boolean null,
    can_start_gateway boolean null,
    min_amount numeric(19,2) null,
    max_amount numeric(19,2) null,
    override_reason varchar(255) null,
    metadata_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz null,
    constraint uk_payment_policy_template_item unique (tenant_id, template_id, payment_method_code),
    constraint ck_ppti_min_nonneg check (min_amount is null or min_amount >= 0),
    constraint ck_ppti_max_nonneg check (max_amount is null or max_amount >= 0),
    constraint ck_ppti_max_ge_min check (max_amount is null or min_amount is null or max_amount >= min_amount)
);

create index if not exists idx_payment_policy_template_items_template on payment_method_policy_template_items (tenant_id, template_id);

alter table payment_method_policy_template_items
    add constraint fk_payment_policy_template_items_tenant
        foreign key (tenant_id) references tenants (id);

alter table payment_method_policy_template_items
    add constraint fk_payment_policy_template_items_template
        foreign key (template_id) references payment_method_policy_templates (id) on delete cascade;

-- 4) Rollouts (histórico operacional)
create table if not exists payment_method_policy_rollouts (
    id bigserial primary key,
    tenant_id bigint not null,
    template_id bigint not null,
    unidade_atendimento_id bigint not null,
    target_device_type varchar(50) null,
    rollout_mode varchar(50) not null,
    overwrite_mode varchar(50) not null,
    dry_run boolean not null default false,
    status varchar(30) not null,
    total_devices_targeted integer not null default 0,
    total_policies_created integer not null default 0,
    total_policies_updated integer not null default 0,
    total_policies_skipped integer not null default 0,
    total_errors integer not null default 0,
    result_json jsonb null,
    started_at timestamptz not null,
    finished_at timestamptz null,
    created_by bigint null
);

create index if not exists idx_payment_policy_rollouts_tenant on payment_method_policy_rollouts (tenant_id, started_at desc);
create index if not exists idx_payment_policy_rollouts_tenant_unidade on payment_method_policy_rollouts (tenant_id, unidade_atendimento_id, started_at desc);

alter table payment_method_policy_rollouts
    add constraint fk_payment_policy_rollouts_tenant
        foreign key (tenant_id) references tenants (id);

alter table payment_method_policy_rollouts
    add constraint fk_payment_policy_rollouts_template
        foreign key (template_id) references payment_method_policy_templates (id);

alter table payment_method_policy_rollouts
    add constraint fk_payment_policy_rollouts_unidade
        foreign key (unidade_atendimento_id) references unidades_atendimento (id);

-- Amarrar device policy a template/rollout quando existir
alter table device_payment_method_policies
    add constraint fk_device_pmp_source_template
        foreign key (source_template_id) references payment_method_policy_templates (id);

alter table device_payment_method_policies
    add constraint fk_device_pmp_source_rollout
        foreign key (source_rollout_id) references payment_method_policy_rollouts (id);

