-- Prompt 43: Fiscalidade/IVA tenant-aware (base mínima)
-- Date: 2026-05-24

create table if not exists tenant_fiscal_profiles (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    status varchar(30) not null,
    fiscal_regime varchar(40) not null,
    taxpayer_number varchar(40),
    legal_name varchar(255),
    commercial_name varchar(255),
    country_code varchar(2) not null default 'AO',
    province varchar(120),
    municipality varchar(120),
    address varchar(255),
    default_tax_policy_id bigint,
    invoice_required boolean not null default false,
    fiscal_document_enabled boolean not null default false,

    primary key (id),
    constraint fk_tenant_fiscal_profile_tenant foreign key (tenant_id) references tenants
);

create unique index if not exists uq_tenant_fiscal_profile_tenant on tenant_fiscal_profiles (tenant_id);

create table if not exists tax_rates (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    country_code varchar(2) not null,
    tax_type varchar(30) not null,
    code varchar(80) not null,
    name varchar(255) not null,
    rate numeric(9, 4) not null,
    status varchar(30) not null,
    effective_from timestamp(6),
    effective_to timestamp(6),
    legal_reference varchar(255),

    primary key (id),
    constraint ck_tax_rate_nonneg check (rate >= 0)
);

create unique index if not exists uq_tax_rates_code on tax_rates (country_code, code);
create index if not exists idx_tax_rates_country_type_status on tax_rates (country_code, tax_type, status);

create table if not exists tenant_tax_policies (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    name varchar(255) not null,
    fiscal_regime varchar(40) not null,
    default_tax_rate_id bigint,
    prices_include_tax boolean not null default false,
    allow_tax_exempt_items boolean not null default true,
    require_tax_document_on_payment boolean not null default false,
    status varchar(30) not null,
    effective_from timestamp(6),
    effective_to timestamp(6),

    primary key (id),
    constraint fk_tenant_tax_policy_tenant foreign key (tenant_id) references tenants,
    constraint fk_tenant_tax_policy_tax_rate foreign key (default_tax_rate_id) references tax_rates
);

create index if not exists idx_tenant_tax_policy_tenant_status on tenant_tax_policies (tenant_id, status);

create table if not exists product_tax_classifications (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    product_id bigint not null,
    tax_rate_id bigint,
    tax_category varchar(40) not null,
    exempt_reason varchar(255),
    effective_from timestamp(6),
    effective_to timestamp(6),
    status varchar(30) not null,

    primary key (id),
    constraint fk_product_tax_class_tenant foreign key (tenant_id) references tenants,
    constraint fk_product_tax_class_product foreign key (product_id) references produtos,
    constraint fk_product_tax_class_tax_rate foreign key (tax_rate_id) references tax_rates
);

create unique index if not exists uq_product_tax_class_one_active
    on product_tax_classifications (tenant_id, product_id)
    where status = 'ACTIVE';

create index if not exists idx_product_tax_class_tenant_product on product_tax_classifications (tenant_id, product_id);

create table if not exists fiscal_document_sequences (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    unidade_atendimento_id bigint,
    document_type varchar(40) not null,
    series varchar(20) not null,
    year integer not null,
    current_number bigint not null default 0,
    status varchar(30) not null,

    primary key (id),
    constraint fk_fiscal_seq_tenant foreign key (tenant_id) references tenants,
    constraint fk_fiscal_seq_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint ck_fiscal_seq_current_nonneg check (current_number >= 0)
);

create unique index if not exists uq_fiscal_seq_key
    on fiscal_document_sequences (tenant_id, unidade_atendimento_id, document_type, series, year);

create table if not exists fiscal_documents (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint,
    unidade_atendimento_id bigint,
    turno_operacional_id bigint,
    sessao_consumo_id bigint,
    pedido_id bigint,
    pagamento_id bigint,
    caixa_operador_session_id bigint,

    document_type varchar(50) not null,
    status varchar(30) not null,
    fiscal_regime varchar(40) not null,
    document_number varchar(60) not null,
    series varchar(20) not null,
    issued_at timestamp(6),

    customer_name varchar(255),
    customer_taxpayer_number varchar(40),

    subtotal_amount numeric(19, 2) not null default 0,
    taxable_amount numeric(19, 2) not null default 0,
    exempt_amount numeric(19, 2) not null default 0,
    tax_amount numeric(19, 2) not null default 0,
    total_amount numeric(19, 2) not null default 0,
    currency varchar(10) not null default 'AOA',

    source varchar(30) not null,
    created_by_user_id bigint,
    operational_device_id bigint,

    primary key (id),
    constraint fk_fiscal_doc_tenant foreign key (tenant_id) references tenants,
    constraint fk_fiscal_doc_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_fiscal_doc_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_fiscal_doc_turno foreign key (turno_operacional_id) references turnos_operacionais,
    constraint fk_fiscal_doc_sessao foreign key (sessao_consumo_id) references sessoes_consumo,
    constraint fk_fiscal_doc_pedido foreign key (pedido_id) references pedidos,
    constraint fk_fiscal_doc_pagamento foreign key (pagamento_id) references pagamentos_gateway,
    constraint fk_fiscal_doc_caixa foreign key (caixa_operador_session_id) references caixa_operador_sessions,
    constraint fk_fiscal_doc_user foreign key (created_by_user_id) references users,
    constraint fk_fiscal_doc_device foreign key (operational_device_id) references dispositivos_operacionais,
    constraint ck_fiscal_doc_amounts_nonneg check (subtotal_amount >= 0 and taxable_amount >= 0 and exempt_amount >= 0 and tax_amount >= 0 and total_amount >= 0)
);

create unique index if not exists uq_fiscal_doc_number on fiscal_documents (tenant_id, series, document_number);
create unique index if not exists uq_fiscal_doc_by_pagamento on fiscal_documents (tenant_id, pagamento_id) where pagamento_id is not null;
create unique index if not exists uq_fiscal_doc_by_pedido_no_pg on fiscal_documents (tenant_id, pedido_id) where pedido_id is not null and pagamento_id is null;

create index if not exists idx_fiscal_doc_tenant_issued_at on fiscal_documents (tenant_id, issued_at);
create index if not exists idx_fiscal_doc_turno on fiscal_documents (tenant_id, turno_operacional_id);
create index if not exists idx_fiscal_doc_pedido on fiscal_documents (tenant_id, pedido_id);
create index if not exists idx_fiscal_doc_status on fiscal_documents (tenant_id, status);

create table if not exists fiscal_document_lines (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    fiscal_document_id bigint not null,
    tenant_id bigint not null,
    product_id bigint,
    pedido_item_id bigint,
    description varchar(255) not null,
    quantity integer not null,
    unit_price numeric(19, 2) not null,
    net_amount numeric(19, 2) not null,
    tax_rate_id bigint,
    tax_rate_code varchar(80),
    tax_rate_value numeric(9, 4),
    tax_amount numeric(19, 2) not null,
    gross_amount numeric(19, 2) not null,
    tax_category varchar(40),
    exempt_reason varchar(255),

    primary key (id),
    constraint fk_fiscal_line_doc foreign key (fiscal_document_id) references fiscal_documents,
    constraint fk_fiscal_line_tenant foreign key (tenant_id) references tenants,
    constraint fk_fiscal_line_product foreign key (product_id) references produtos,
    constraint fk_fiscal_line_item foreign key (pedido_item_id) references itens_pedido,
    constraint fk_fiscal_line_tax_rate foreign key (tax_rate_id) references tax_rates,
    constraint ck_fiscal_line_qty_pos check (quantity > 0),
    constraint ck_fiscal_line_amounts_nonneg check (unit_price >= 0 and net_amount >= 0 and tax_amount >= 0 and gross_amount >= 0)
);

create index if not exists idx_fiscal_line_doc on fiscal_document_lines (tenant_id, fiscal_document_id);
