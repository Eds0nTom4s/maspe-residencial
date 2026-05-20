-- Prompt 36: Idempotência para confirmação manual de OrdemPagamento (CASH/TPA)

create table if not exists ordem_pagamento_manual_idempotency_records (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    device_id bigint not null,
    ordem_pagamento_id bigint not null,

    idempotency_key varchar(120) not null,
    client_request_id varchar(160) not null,
    request_hash varchar(64) not null,

    status varchar(20) not null,
    error_code varchar(80),

    primary key (id),
    constraint fk_ordem_pg_idem_tenant foreign key (tenant_id) references tenants,
    constraint fk_ordem_pg_idem_device foreign key (device_id) references dispositivos_operacionais,
    constraint fk_ordem_pg_idem_ordem foreign key (ordem_pagamento_id) references ordens_pagamento,
    constraint uk_ordem_pg_idem_key unique (tenant_id, device_id, idempotency_key),
    constraint uk_ordem_pg_idem_client_request unique (tenant_id, device_id, client_request_id)
);

create index if not exists idx_ordem_pg_idem_tenant_device on ordem_pagamento_manual_idempotency_records (tenant_id, device_id);
create index if not exists idx_ordem_pg_idem_ordem on ordem_pagamento_manual_idempotency_records (ordem_pagamento_id);
create index if not exists idx_ordem_pg_idem_created_at on ordem_pagamento_manual_idempotency_records (created_at);

