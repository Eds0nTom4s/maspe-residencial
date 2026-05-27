-- CONSUMA Tenant Core (V8)
-- Hardening for public QR order creation:
-- - Idempotency table (tenant + qr + idempotency_key)
-- - Per-tenant daily sequence counter for pedido numbering
-- Date: 2026-05-16
-- IMPORTANT:
-- - Does NOT create payments.
-- - Does NOT change legacy QR token flows.

create table if not exists public_qr_order_requests (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    qr_code_operacional_id bigint not null,
    idempotency_key varchar(100) not null,
    request_hash varchar(64) not null,
    pedido_id bigint,
    status varchar(20) not null,

    primary key (id),
    constraint uk_public_qr_order_idem unique (tenant_id, qr_code_operacional_id, idempotency_key),
    constraint fk_public_qr_order_tenant foreign key (tenant_id) references tenants,
    constraint fk_public_qr_order_qr foreign key (qr_code_operacional_id) references qr_codes_operacionais,
    constraint fk_public_qr_order_pedido foreign key (pedido_id) references pedidos
);

create index if not exists idx_public_qr_order_tenant_created_at on public_qr_order_requests (tenant_id, created_at);
create index if not exists idx_public_qr_order_qr on public_qr_order_requests (qr_code_operacional_id);
create index if not exists idx_public_qr_order_pedido on public_qr_order_requests (pedido_id);

create table if not exists pedido_sequence_counters (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    data_referencia date not null,
    proximo_numero bigint not null,

    primary key (id),
    constraint uk_pedido_seq_tenant_data unique (tenant_id, data_referencia),
    constraint fk_pedido_seq_tenant foreign key (tenant_id) references tenants
);

create index if not exists idx_pedido_seq_tenant on pedido_sequence_counters (tenant_id);
