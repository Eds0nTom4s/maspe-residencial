-- CONSUMA Tenant Core (V10)
-- Adds Pedido financial status PENDENTE_PAGAMENTO and introduces raw callback logs for AppyPay.
-- Date: 2026-05-16
-- IMPORTANT: does not implement reconciliation/settlement.

-- 1) Update check constraint for pedidos.status_financeiro to include PENDENTE_PAGAMENTO
alter table pedidos drop constraint if exists pedidos_status_financeiro_check;
alter table pedidos
    add constraint pedidos_status_financeiro_check
    check (status_financeiro in ('NAO_PAGO','PENDENTE_PAGAMENTO','PAGO','ESTORNADO'));

-- 2) Raw callback logs
create table pagamento_callback_logs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint,
    pagamento_id bigint,
    provider varchar(30) not null,
    external_reference varchar(15),
    gateway_charge_id varchar(100),
    status_recebido varchar(40),

    headers_json text,
    payload_json text,
    raw_body text,

    signature_valid boolean,
    processed boolean not null,
    processing_status varchar(30) not null,
    processing_error varchar(500),
    received_at timestamp(6) not null,
    processed_at timestamp(6),

    primary key (id),
    constraint fk_callback_log_tenant foreign key (tenant_id) references tenants,
    constraint fk_callback_log_pagamento foreign key (pagamento_id) references pagamentos_gateway
);

create index idx_callback_log_received_at on pagamento_callback_logs (received_at);
create index idx_callback_log_external_ref on pagamento_callback_logs (external_reference);
create index idx_callback_log_pagamento on pagamento_callback_logs (pagamento_id);
create index idx_callback_log_tenant on pagamento_callback_logs (tenant_id);

