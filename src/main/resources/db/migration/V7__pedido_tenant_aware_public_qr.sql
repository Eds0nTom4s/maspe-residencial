-- CONSUMA Tenant Core (V7)
-- Makes Pedido/SubPedido/ItemPedido tenant-aware for public QR order creation.
-- Date: 2026-05-16
-- IMPORTANT:
-- - Does NOT change payment tables/flows.
-- - Does NOT refactor legacy services; backfill keeps existing rows compatible.

-- 1) Add tenant_id columns (nullable first for backfill)
alter table pedidos add column if not exists tenant_id bigint;
alter table sub_pedidos add column if not exists tenant_id bigint;
alter table itens_pedido add column if not exists tenant_id bigint;

-- 2) Indexes (tenant scoping)
create index if not exists idx_pedido_tenant on pedidos (tenant_id);
create index if not exists idx_pedido_tenant_status on pedidos (tenant_id, status);
create index if not exists idx_pedido_tenant_status_financeiro on pedidos (tenant_id, status_financeiro);
create index if not exists idx_pedido_tenant_created_at on pedidos (tenant_id, created_at);

create index if not exists idx_subpedido_tenant on sub_pedidos (tenant_id);
create index if not exists idx_subpedido_tenant_status on sub_pedidos (tenant_id, status);

create index if not exists idx_item_pedido_tenant on itens_pedido (tenant_id);
create index if not exists idx_item_pedido_tenant_pedido on itens_pedido (tenant_id, pedido_id);
create index if not exists idx_item_pedido_tenant_produto on itens_pedido (tenant_id, produto_id);

-- 3) Backfill pedidos.tenant_id via SessaoConsumo -> Instituicao -> Tenant (with safe fallbacks)
update pedidos p
set tenant_id = coalesce(
    inst.tenant_id,
    inst_mesa.tenant_id,
    inst_ua.tenant_id,
    (select id from tenants where tenant_code = 'LEGACY')
)
from sessoes_consumo s
left join instituicoes inst on inst.id = s.instituicao_id
left join mesas m on m.id = s.mesa_id
left join instituicoes inst_mesa on inst_mesa.id = m.instituicao_id
left join unidades_atendimento ua on ua.id = s.unidade_atendimento_id
left join instituicoes inst_ua on inst_ua.id = ua.instituicao_id
where p.sessao_consumo_id = s.id
  and p.tenant_id is null;

update pedidos
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

-- 4) Backfill sub_pedidos.tenant_id from pedido
update sub_pedidos sp
set tenant_id = p.tenant_id
from pedidos p
where sp.pedido_id = p.id
  and sp.tenant_id is null;

update sub_pedidos
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

-- 5) Backfill itens_pedido.tenant_id from pedido/subpedido/produto
update itens_pedido ip
set tenant_id = coalesce(
    (select p.tenant_id from pedidos p where p.id = ip.pedido_id),
    (select sp.tenant_id from sub_pedidos sp where sp.id = ip.sub_pedido_id),
    (select pr.tenant_id from produtos pr where pr.id = ip.produto_id),
    (select id from tenants where tenant_code = 'LEGACY')
)
where ip.tenant_id is null;

update itens_pedido
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

-- 6) Enforce NOT NULL + FK
alter table pedidos alter column tenant_id set not null;
alter table sub_pedidos alter column tenant_id set not null;
alter table itens_pedido alter column tenant_id set not null;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_pedido_tenant') then
        alter table pedidos
            add constraint fk_pedido_tenant
            foreign key (tenant_id) references tenants;
    end if;
end $$;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_subpedido_tenant') then
        alter table sub_pedidos
            add constraint fk_subpedido_tenant
            foreign key (tenant_id) references tenants;
    end if;
end $$;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_item_pedido_tenant') then
        alter table itens_pedido
            add constraint fk_item_pedido_tenant
            foreign key (tenant_id) references tenants;
    end if;
end $$;
