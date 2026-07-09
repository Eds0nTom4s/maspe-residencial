-- Prompt 30: POS/Device cria pedido online (idempotência por device)

create table if not exists device_pedido_idempotency_records (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    device_id bigint not null,

    idempotency_key varchar(120) not null,
    client_request_id varchar(160) not null,
    request_hash varchar(64) not null,

    pedido_id bigint,
    status varchar(20) not null,
    error_code varchar(80),

    primary key (id),
    constraint fk_device_pedido_idem_tenant foreign key (tenant_id) references tenants,
    constraint fk_device_pedido_idem_device foreign key (device_id) references dispositivos_operacionais,
    constraint fk_device_pedido_idem_pedido foreign key (pedido_id) references pedidos,
    constraint uk_device_pedido_idem_key unique (tenant_id, device_id, idempotency_key),
    constraint uk_device_pedido_client_request unique (tenant_id, device_id, client_request_id)
);

create index if not exists idx_device_pedido_idem_tenant_device on device_pedido_idempotency_records (tenant_id, device_id);
create index if not exists idx_device_pedido_idem_pedido on device_pedido_idempotency_records (pedido_id);
create index if not exists idx_device_pedido_idem_created_at on device_pedido_idempotency_records (created_at);
