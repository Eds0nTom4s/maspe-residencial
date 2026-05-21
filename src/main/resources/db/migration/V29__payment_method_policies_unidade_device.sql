-- Prompt 38.2: Políticas de métodos de pagamento por UnidadeAtendimento e por DispositivoOperacional

create table if not exists unidade_payment_method_policies (
    id bigserial primary key,
    tenant_id bigint not null,
    unidade_atendimento_id bigint not null,
    payment_method_code varchar(50) not null,
    status varchar(30) not null,
    enabled_for_qr boolean null,
    enabled_for_pos boolean null,
    enabled_for_pedido boolean null,
    enabled_for_fundo_consumo boolean null,
    min_amount numeric(19,2) null,
    max_amount numeric(19,2) null,
    inherit_from_tenant boolean not null default true,
    override_reason varchar(255) null,
    metadata_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz null,
    created_by bigint null,
    updated_by bigint null,
    constraint uk_unidade_payment_method_policy unique (tenant_id, unidade_atendimento_id, payment_method_code),
    constraint ck_unidade_pmp_min_nonneg check (min_amount is null or min_amount >= 0),
    constraint ck_unidade_pmp_max_nonneg check (max_amount is null or max_amount >= 0),
    constraint ck_unidade_pmp_max_ge_min check (max_amount is null or min_amount is null or max_amount >= min_amount)
);

create index if not exists idx_unidade_pmp_tenant on unidade_payment_method_policies (tenant_id);
create index if not exists idx_unidade_pmp_tenant_unidade on unidade_payment_method_policies (tenant_id, unidade_atendimento_id);
create index if not exists idx_unidade_pmp_tenant_code on unidade_payment_method_policies (tenant_id, payment_method_code);

alter table unidade_payment_method_policies
    add constraint fk_unidade_pmp_tenant
        foreign key (tenant_id) references tenants (id);

alter table unidade_payment_method_policies
    add constraint fk_unidade_pmp_unidade
        foreign key (unidade_atendimento_id) references unidades_atendimento (id);

create table if not exists device_payment_method_policies (
    id bigserial primary key,
    tenant_id bigint not null,
    dispositivo_operacional_id bigint not null,
    unidade_atendimento_id bigint not null,
    payment_method_code varchar(50) not null,
    status varchar(30) not null,
    enabled_for_pos boolean null,
    enabled_for_pedido boolean null,
    enabled_for_fundo_consumo boolean null,
    can_confirm_manual boolean null,
    can_start_gateway boolean null,
    min_amount numeric(19,2) null,
    max_amount numeric(19,2) null,
    inherit_from_unidade boolean not null default true,
    override_reason varchar(255) null,
    metadata_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz null,
    created_by bigint null,
    updated_by bigint null,
    constraint uk_device_payment_method_policy unique (tenant_id, dispositivo_operacional_id, payment_method_code),
    constraint ck_device_pmp_min_nonneg check (min_amount is null or min_amount >= 0),
    constraint ck_device_pmp_max_nonneg check (max_amount is null or max_amount >= 0),
    constraint ck_device_pmp_max_ge_min check (max_amount is null or min_amount is null or max_amount >= min_amount)
);

create index if not exists idx_device_pmp_tenant on device_payment_method_policies (tenant_id);
create index if not exists idx_device_pmp_tenant_device on device_payment_method_policies (tenant_id, dispositivo_operacional_id);
create index if not exists idx_device_pmp_tenant_unidade on device_payment_method_policies (tenant_id, unidade_atendimento_id);
create index if not exists idx_device_pmp_tenant_code on device_payment_method_policies (tenant_id, payment_method_code);

alter table device_payment_method_policies
    add constraint fk_device_pmp_tenant
        foreign key (tenant_id) references tenants (id);

alter table device_payment_method_policies
    add constraint fk_device_pmp_device
        foreign key (dispositivo_operacional_id) references dispositivos_operacionais (id);

alter table device_payment_method_policies
    add constraint fk_device_pmp_unidade
        foreign key (unidade_atendimento_id) references unidades_atendimento (id);

