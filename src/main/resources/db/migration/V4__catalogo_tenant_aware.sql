-- CONSUMA Tenant Core (V4)
-- Makes catalog tenant-aware:
-- - Introduces categoria_produtos (tenant-owned)
-- - Links produtos to tenant (tenant_id NOT NULL)
-- - Changes unique constraint: produtos.codigo becomes unique per tenant
-- Date: 2026-05-15
-- IMPORTANT:
-- - This migration keeps legacy enum-based categoria column for compatibility.
-- - categoria_produto_id is introduced and backfilled when possible, but remains nullable for incremental rollout.

-- 1) categoria_produtos (tenant-owned)
create table if not exists categoria_produtos (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    nome varchar(150) not null,
    slug varchar(80) not null,
    descricao text,
    ordem integer not null,
    ativo boolean not null,

    primary key (id),
    constraint fk_categoria_produto_tenant foreign key (tenant_id) references tenants,
    constraint uk_categoria_produto_tenant_slug unique (tenant_id, slug)
);

create index if not exists idx_categoria_produtos_tenant on categoria_produtos (tenant_id);
create index if not exists idx_categoria_produtos_tenant_ativo on categoria_produtos (tenant_id, ativo);

-- 2) produtos: add tenant_id + categoria_produto_id
alter table produtos add column if not exists tenant_id bigint;
alter table produtos add column if not exists categoria_produto_id bigint;

create index if not exists idx_produtos_tenant on produtos (tenant_id);
create index if not exists idx_produtos_tenant_ativo on produtos (tenant_id, ativo);
create index if not exists idx_produtos_tenant_disponivel_ativo on produtos (tenant_id, disponivel, ativo);
create index if not exists idx_produtos_tenant_categoria_produto on produtos (tenant_id, categoria_produto_id);
create index if not exists idx_produtos_tenant_categoria_enum on produtos (tenant_id, categoria);

-- 3) Backfill: associate existing produtos to LEGACY tenant (created in V3)
update produtos
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

alter table produtos alter column tenant_id set not null;

alter table produtos
    add constraint fk_produto_tenant
    foreign key (tenant_id) references tenants;

alter table produtos
    add constraint fk_produto_categoria_produto
    foreign key (categoria_produto_id) references categoria_produtos;

-- 4) Seed default categorias for LEGACY tenant (from legacy enum values)
with legacy_tenant as (
    select id as tenant_id from tenants where tenant_code = 'LEGACY'
),
rows as (
    select 'ENTRADA'::varchar as enum_name, 'Entrada'::varchar as nome, 'entrada'::varchar as slug, 10::int as ordem
    union all select 'PRATO_PRINCIPAL', 'Prato Principal', 'prato-principal', 20
    union all select 'ACOMPANHAMENTO', 'Acompanhamento', 'acompanhamento', 30
    union all select 'SOBREMESA', 'Sobremesa', 'sobremesa', 40
    union all select 'BEBIDA_ALCOOLICA', 'Bebida Alcoólica', 'bebida-alcoolica', 50
    union all select 'BEBIDA_NAO_ALCOOLICA', 'Bebida Não Alcoólica', 'bebida-nao-alcoolica', 60
    union all select 'LANCHE', 'Lanche', 'lanche', 70
    union all select 'PIZZA', 'Pizza', 'pizza', 80
    union all select 'OUTROS', 'Outros', 'outros', 90
)
insert into categoria_produtos (
    created_at, created_by, version,
    tenant_id, nome, slug, descricao, ordem, ativo
)
select
    now(), 'system', 0,
    lt.tenant_id, r.nome, r.slug, null, r.ordem, true
from legacy_tenant lt
join rows r on true
where not exists (
    select 1 from categoria_produtos cp
    where cp.tenant_id = lt.tenant_id and cp.slug = r.slug
);

-- 5) Backfill produtos.categoria_produto_id (best-effort mapping)
update produtos p
set categoria_produto_id = cp.id
from categoria_produtos cp
where p.categoria_produto_id is null
  and cp.tenant_id = p.tenant_id
  and (
      (p.categoria = 'ENTRADA' and cp.slug = 'entrada')
      or (p.categoria = 'PRATO_PRINCIPAL' and cp.slug = 'prato-principal')
      or (p.categoria = 'ACOMPANHAMENTO' and cp.slug = 'acompanhamento')
      or (p.categoria = 'SOBREMESA' and cp.slug = 'sobremesa')
      or (p.categoria = 'BEBIDA_ALCOOLICA' and cp.slug = 'bebida-alcoolica')
      or (p.categoria = 'BEBIDA_NAO_ALCOOLICA' and cp.slug = 'bebida-nao-alcoolica')
      or (p.categoria = 'LANCHE' and cp.slug = 'lanche')
      or (p.categoria = 'PIZZA' and cp.slug = 'pizza')
      or (p.categoria = 'OUTROS' and cp.slug = 'outros')
  );

-- 6) Unique constraint: codigo unique per tenant (drop global unique, create composite unique)
alter table produtos drop constraint if exists produtos_codigo_key;

drop index if exists idx_produto_codigo;

create unique index if not exists uk_produtos_tenant_codigo on produtos (tenant_id, codigo);

