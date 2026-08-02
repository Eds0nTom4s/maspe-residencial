create table if not exists tenant_payment_confirmation_idempotency_records (
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
    constraint fk_tenant_payment_idem_tenant foreign key (tenant_id) references tenants,
    constraint fk_tenant_payment_idem_user foreign key (user_id) references users,
    constraint fk_tenant_payment_idem_order foreign key (ordem_pagamento_id) references ordens_pagamento,
    constraint uk_tenant_payment_idem_key unique (tenant_id, user_id, idempotency_key),
    constraint uk_tenant_payment_client_request unique (tenant_id, user_id, client_request_id)
);

create index if not exists idx_tenant_payment_idem_actor
    on tenant_payment_confirmation_idempotency_records (tenant_id, user_id);
create index if not exists idx_tenant_payment_idem_order
    on tenant_payment_confirmation_idempotency_records (ordem_pagamento_id);
create index if not exists idx_tenant_payment_idem_created
    on tenant_payment_confirmation_idempotency_records (created_at);
