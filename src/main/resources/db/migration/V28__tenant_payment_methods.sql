-- Prompt 38: Métodos de pagamento tenant-aware

create table if not exists tenant_payment_methods (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,

    code varchar(50) not null,
    display_name varchar(100) not null,
    description varchar(255),

    status varchar(30) not null,
    type varchar(50) not null,
    confirmation_mode varchar(50) not null,
    provider varchar(50) not null,

    enabled_for_qr boolean not null default false,
    enabled_for_pos boolean not null default true,
    enabled_for_pedido boolean not null default true,
    enabled_for_fundo_consumo boolean not null default true,

    requires_open_turno boolean not null default false,
    requires_gateway boolean not null default false,
    requires_manual_confirmation boolean not null default false,

    min_amount numeric(19,2),
    max_amount numeric(19,2),
    currency varchar(10) not null default 'AOA',
    sort_order integer not null default 100,
    icon_key varchar(80),
    metadata_json jsonb,

    primary key (id),
    constraint fk_tenant_payment_methods_tenant foreign key (tenant_id) references tenants,
    constraint uk_tenant_payment_methods_tenant_code unique (tenant_id, code),
    constraint ck_tenant_payment_methods_min_amount_nonneg check (min_amount is null or min_amount >= 0),
    constraint ck_tenant_payment_methods_max_amount_nonneg check (max_amount is null or max_amount >= 0),
    constraint ck_tenant_payment_methods_min_le_max check (max_amount is null or min_amount is null or max_amount >= min_amount)
);

create index if not exists idx_tenant_payment_methods_tenant on tenant_payment_methods (tenant_id);
create index if not exists idx_tenant_payment_methods_tenant_status on tenant_payment_methods (tenant_id, status);
create index if not exists idx_tenant_payment_methods_tenant_code on tenant_payment_methods (tenant_id, code);

