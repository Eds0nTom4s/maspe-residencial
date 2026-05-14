-- CONSUMA Tenant Core (V3)
-- Links instituicoes to tenants (Tenant 1:N Instituicao)
-- Date: 2026-05-14
-- IMPORTANT:
-- - This migration does NOT introduce TenantContext/TenantResolver/TenantGuard.
-- - This migration keeps legacy flows working by backfilling existing instituicoes to a LEGACY tenant.

-- 1) Add tenant_id as nullable first (compatibility for existing rows)
alter table instituicoes add column if not exists tenant_id bigint;

create index if not exists idx_instituicao_tenant on instituicoes (tenant_id);

-- 2) Create LEGACY tenant if it doesn't exist (used for backfill of existing instituicoes)
insert into tenants (
    created_at, created_by, version,
    nome, slug, tenant_code, nif, telefone, email, tipo, estado
)
select
    now(), 'system', 0,
    'LEGACY (single-tenant)', 'legacy-single-tenant', 'LEGACY', null, null, null, 'INSTITUCIONAL', 'ATIVO'
where not exists (select 1 from tenants where tenant_code = 'LEGACY');

-- 3) Ensure LEGACY tenant has an active subscription to Plano PILOTO (optional but keeps SaaS domain consistent)
insert into subscricoes (
    created_at, created_by, version,
    tenant_id, plano_id, estado, inicio_em, fim_em, renovacao_automatica
)
select
    now(), 'system', 0,
    t.id, p.id, 'ATIVA', current_date, null, false
from tenants t
join planos p on p.codigo = 'PILOTO'
where t.tenant_code = 'LEGACY'
  and not exists (
      select 1 from subscricoes s
      where s.tenant_id = t.id and s.estado = 'ATIVA'
  );

-- 4) Backfill: set tenant_id for existing instituicoes
update instituicoes
set tenant_id = (select id from tenants where tenant_code = 'LEGACY')
where tenant_id is null;

-- 5) Enforce NOT NULL and FK
alter table instituicoes alter column tenant_id set not null;

alter table instituicoes
    add constraint fk_instituicao_tenant
    foreign key (tenant_id) references tenants;

