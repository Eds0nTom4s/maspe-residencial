-- CONSUMA Tenant Core (V9)
-- Makes Pagamento tenant-aware and introduces idempotency for public QR payment start.
-- Date: 2026-05-16
-- IMPORTANT:
-- - Does NOT implement callback confirmation (Prompt 9.2).
-- - Does NOT remove legacy payment flows.

-- 1) Add tenant_id to pagamentos_gateway (nullable first for backfill)
alter table pagamentos_gateway add column if not exists tenant_id bigint;

create index if not exists idx_pagamento_tenant on pagamentos_gateway (tenant_id);
create index if not exists idx_pagamento_tenant_status on pagamentos_gateway (tenant_id, status);
create index if not exists idx_pagamento_tenant_external_ref on pagamentos_gateway (tenant_id, external_reference);
create index if not exists idx_pagamento_tenant_created_at on pagamentos_gateway (tenant_id, created_at);

-- 2) Backfill tenant_id
-- Prefer: pagamento -> pedido -> tenant
update pagamentos_gateway pg
set tenant_id = p.tenant_id
from pedidos p
where pg.pedido_id = p.id
  and pg.tenant_id is null
  and p.tenant_id is not null;

-- Fallback: pagamento -> fundo -> sessao -> instituicao -> tenant
update pagamentos_gateway pg
set tenant_id = coalesce(
    inst.tenant_id,
    inst_mesa.tenant_id,
    inst_ua.tenant_id,
    (select id from tenants where tenant_code = 'LEGACY')
)
from fundos_consumo f
join sessoes_consumo s on s.id = f.sessao_consumo_id
left join instituicoes inst on inst.id = s.instituicao_id
left join mesas m on m.id = s.mesa_id
left join instituicoes inst_mesa on inst_mesa.id = m.instituicao_id
left join unidades_atendimento ua on ua.id = s.unidade_atendimento_id
left join instituicoes inst_ua on inst_ua.id = ua.instituicao_id
where pg.fundo_consumo_id = f.id
  and pg.tenant_id is null;

-- Final fallback: LEGACY
update pagamentos_gateway
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

-- 3) Enforce NOT NULL and FK
alter table pagamentos_gateway alter column tenant_id set not null;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_pagamento_tenant') then
        alter table pagamentos_gateway
            add constraint fk_pagamento_tenant
            foreign key (tenant_id) references tenants;
    end if;
end $$;

-- 4) Idempotency table for public QR payment start
create table if not exists public_qr_payment_requests (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    pedido_id bigint not null,
    idempotency_key varchar(100) not null,
    request_hash varchar(64) not null,
    pagamento_id bigint,
    status varchar(20) not null,

    primary key (id),
    constraint uk_public_qr_payment_idem unique (tenant_id, pedido_id, idempotency_key),
    constraint fk_public_qr_payment_tenant foreign key (tenant_id) references tenants,
    constraint fk_public_qr_payment_pedido foreign key (pedido_id) references pedidos,
    constraint fk_public_qr_payment_pagamento foreign key (pagamento_id) references pagamentos_gateway
);

create index if not exists idx_public_qr_payment_tenant_created_at on public_qr_payment_requests (tenant_id, created_at);
create index if not exists idx_public_qr_payment_pedido on public_qr_payment_requests (pedido_id);
create index if not exists idx_public_qr_payment_pagamento on public_qr_payment_requests (pagamento_id);
