-- Reconciled successor of the historical 20260802-20260804 PDV migrations.
-- The historical versions are intentionally not reused because main already reached 20260808.01.

alter table users
    add column if not exists must_change_password boolean not null default false,
    add column if not exists password_reset_required boolean not null default false,
    add column if not exists temporary_password_expires_at timestamp,
    add column if not exists last_password_changed_at timestamp;

create table tenant_payment_confirmation_idempotency_records (
    id bigserial primary key,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),
    tenant_id bigint not null,
    user_id bigint not null,
    ordem_pagamento_id bigint not null,
    idempotency_key varchar(120) not null,
    client_request_id varchar(160) not null,
    request_hash varchar(64) not null,
    status varchar(20) not null,
    constraint fk_tenant_payment_idem_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_tenant_payment_idem_user foreign key (user_id) references users(id),
    constraint fk_tenant_payment_idem_order foreign key (ordem_pagamento_id) references ordens_pagamento(id),
    constraint uk_tenant_payment_idem_key unique (tenant_id, user_id, idempotency_key),
    constraint uk_tenant_payment_client_request unique (tenant_id, user_id, client_request_id),
    constraint ck_tenant_payment_idem_status check (status in ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

create index idx_tenant_payment_idem_actor
    on tenant_payment_confirmation_idempotency_records (tenant_id, user_id);
create index idx_tenant_payment_idem_order
    on tenant_payment_confirmation_idempotency_records (ordem_pagamento_id);
create index idx_tenant_payment_idem_created
    on tenant_payment_confirmation_idempotency_records (created_at);

create table tenant_pdv_order_idempotency_records (
    id bigserial primary key,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),
    tenant_id bigint not null,
    user_id bigint not null,
    pedido_id bigint,
    idempotency_key varchar(120) not null,
    client_request_id varchar(160) not null,
    request_hash varchar(64) not null,
    status varchar(20) not null,
    constraint fk_tenant_pdv_order_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_tenant_pdv_order_user foreign key (user_id) references users(id),
    constraint fk_tenant_pdv_order_pedido foreign key (pedido_id) references pedidos(id),
    constraint uk_tenant_pdv_order_idem_key unique (tenant_id, user_id, idempotency_key),
    constraint uk_tenant_pdv_order_client_request unique (tenant_id, user_id, client_request_id),
    constraint ck_tenant_pdv_order_status check (status in ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

create index idx_tenant_pdv_order_actor
    on tenant_pdv_order_idempotency_records (tenant_id, user_id);
create index idx_tenant_pdv_order_pedido
    on tenant_pdv_order_idempotency_records (pedido_id);
create index idx_tenant_pdv_order_created
    on tenant_pdv_order_idempotency_records (created_at);

alter table caixa_operador_sessions
    alter column operational_device_id drop not null;

alter table caixa_operador_sessions
    add column channel varchar(20) not null default 'DEVICE_POS';

create unique index uq_caixa_operador_web_open
    on caixa_operador_sessions (tenant_id, operador_user_id)
    where status = 'OPEN' and channel = 'WEB_PDV';

alter table caixa_operador_sessions
    add constraint ck_caixa_operador_channel_device
    check ((channel = 'DEVICE_POS' and operational_device_id is not null)
        or (channel = 'WEB_PDV' and operational_device_id is null));

alter table ordens_pagamento
    add column metodo_confirmado varchar(20),
    add column valor_recebido numeric(19, 2),
    add column troco numeric(19, 2);

alter table ordens_pagamento
    add constraint ck_ordem_pg_valor_recebido_nonneg
        check (valor_recebido is null or valor_recebido >= 0),
    add constraint ck_ordem_pg_troco_nonneg
        check (troco is null or troco >= 0);

alter table fiscal_documents
    add column public_share_token_hash varchar(64),
    add column public_share_expires_at timestamp;

create unique index uq_fiscal_document_public_share_token
    on fiscal_documents(public_share_token_hash)
    where public_share_token_hash is not null;

create index idx_fiscal_document_public_share_expiry
    on fiscal_documents(public_share_expires_at)
    where public_share_token_hash is not null;
