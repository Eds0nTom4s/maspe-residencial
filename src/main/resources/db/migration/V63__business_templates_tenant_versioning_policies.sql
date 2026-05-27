-- Prompt 39: Business Templates / Modelos Operacionais
-- - versionamento do tenant (template_code/template_version/provisioned_*)
-- - políticas operacionais agregadas por tenant

alter table tenants add column if not exists template_code varchar(60);
alter table tenants add column if not exists template_version integer;
alter table tenants add column if not exists provisioned_at timestamp(6);
alter table tenants add column if not exists provisioned_by varchar(120);
alter table tenants add column if not exists provisioning_source varchar(80);

create table if not exists tenant_operacao_policies (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,

    require_open_turno_for_orders boolean not null default false,
    logistics_mode varchar(40) not null default 'NONE',
    allow_pickup boolean not null default true,
    allow_manual_payment boolean not null default true,
    allow_digital_payment boolean not null default true,
    stock_mode varchar(20),
    production_enabled boolean not null default false,
    pos_enabled boolean not null default false,
    kds_enabled boolean not null default false,
    allow_table_qr boolean not null default false,
    snapshot_financeiro_enabled boolean not null default false,
    pre_fecho_enabled boolean not null default false,

    primary key (id),
    constraint fk_tenant_operacao_policy_tenant foreign key (tenant_id) references tenants (id)
);

create unique index if not exists uq_tenant_operacao_policy_tenant on tenant_operacao_policies (tenant_id);
