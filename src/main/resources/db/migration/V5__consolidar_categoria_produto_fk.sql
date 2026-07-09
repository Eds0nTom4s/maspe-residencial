-- CONSUMA Tenant Core (V5)
-- Consolidates CategoriaProduto FK as the primary source of truth for Produto categories.
-- Date: 2026-05-15
-- IMPORTANT:
-- - Keeps legacy enum column `produtos.categoria` for backward compatibility.
-- - Ensures `produtos.categoria_produto_id` is populated for ALL rows and (when safe) enforces NOT NULL.

-- 1) Ensure default categories exist for every tenant that has products.
with tenants_with_products as (
    select distinct p.tenant_id
    from produtos p
    where p.tenant_id is not null
),
rows as (
    -- Default category (fallback)
    select 'GERAL'::varchar as enum_name, 'Geral'::varchar as nome, 'geral'::varchar as slug, 0::int as ordem
    union all select 'ENTRADA', 'Entrada', 'entrada', 10
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
    t.tenant_id, r.nome, r.slug, null, r.ordem, true
from tenants_with_products t
join rows r on true
where not exists (
    select 1 from categoria_produtos cp
    where cp.tenant_id = t.tenant_id and cp.slug = r.slug
);

-- 2) Backfill produtos.categoria_produto_id using legacy enum mapping when possible.
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

-- 3) Fallback: any remaining NULL -> assign default "geral" for the tenant.
update produtos p
set categoria_produto_id = cp.id
from categoria_produtos cp
where p.categoria_produto_id is null
  and cp.tenant_id = p.tenant_id
  and cp.slug = 'geral';

-- 4) Enforce NOT NULL when safe (after backfill)
alter table produtos alter column categoria_produto_id set not null;

-- 5) Ensure FK exists (idempotent)
do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_produto_categoria_produto'
    ) then
        alter table produtos
            add constraint fk_produto_categoria_produto
            foreign key (categoria_produto_id) references categoria_produtos;
    end if;
end$$;

-- 6) Ensure index exists (tenant + categoria_produto)
create index if not exists idx_produtos_tenant_categoria_produto on produtos (tenant_id, categoria_produto_id);

