-- CONSUMA Tenant Core (V2)
-- Creates SaaS foundation tables (Tenant/Plano/Subscricao/TenantUser/TenantLimiteOverride)
-- Date: 2026-05-14
-- IMPORTANT: This migration intentionally does NOT link Instituicao to Tenant yet.

create table if not exists tenants (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    nome varchar(160) not null,
    slug varchar(80) not null,
    tenant_code varchar(20) not null,
    nif varchar(30),
    telefone varchar(20),
    email varchar(120),
    tipo varchar(40) not null,
    estado varchar(20) not null,

    primary key (id),
    constraint uk_tenants_slug unique (slug),
    constraint uk_tenants_tenant_code unique (tenant_code)
);

create index if not exists idx_tenants_estado on tenants (estado);

create table if not exists planos (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    codigo varchar(30) not null,
    nome varchar(120) not null,
    descricao text,
    preco_mensal numeric(12,2) not null,

    max_instituicoes integer not null,
    max_unidades_atendimento integer not null,
    max_produtos integer not null,
    max_usuarios integer not null,
    max_qr_codes integer not null,
    max_dispositivos integer not null,

    permite_multi_instituicao boolean not null,
    permite_pedidos_qr boolean not null,
    permite_pos boolean not null,
    permite_offline boolean not null,
    ativo boolean not null,

    primary key (id),
    constraint uk_planos_codigo unique (codigo),
    constraint ck_planos_preco_nao_negativo check (preco_mensal >= 0)
);

create index if not exists idx_planos_ativo on planos (ativo);

create table if not exists subscricoes (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    plano_id bigint not null,
    estado varchar(20) not null,
    inicio_em date not null,
    fim_em date,
    renovacao_automatica boolean not null,

    primary key (id),
    constraint fk_subscricao_tenant foreign key (tenant_id) references tenants,
    constraint fk_subscricao_plano foreign key (plano_id) references planos
);

create index if not exists idx_subscricoes_tenant on subscricoes (tenant_id);
create index if not exists idx_subscricoes_plano on subscricoes (plano_id);
create index if not exists idx_subscricoes_estado on subscricoes (estado);

-- Garantia: no máximo 1 subscrição ATIVA por tenant
create unique index if not exists uk_subscricoes_tenant_ativa on subscricoes (tenant_id) where (estado = 'ATIVA');

create table if not exists tenant_users (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    user_id bigint not null,
    role varchar(30) not null,
    estado varchar(20) not null,
    unidade_atendimento_default_id bigint,

    primary key (id),
    constraint fk_tenant_user_tenant foreign key (tenant_id) references tenants,
    constraint fk_tenant_user_user foreign key (user_id) references users,
    constraint fk_tenant_user_unidade foreign key (unidade_atendimento_default_id) references unidades_atendimento,
    constraint uk_tenant_user_unique unique (tenant_id, user_id, role)
);

create index if not exists idx_tenant_users_tenant on tenant_users (tenant_id);
create index if not exists idx_tenant_users_user on tenant_users (user_id);

create table if not exists tenant_limite_overrides (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    max_instituicoes integer,
    max_unidades_atendimento integer,
    max_produtos integer,
    max_usuarios integer,
    max_qr_codes integer,
    max_dispositivos integer,
    motivo varchar(500),
    configurado_por varchar(120),
    configurado_em timestamp(6),
    ativo boolean not null,

    primary key (id),
    constraint fk_tenant_limite_override_tenant foreign key (tenant_id) references tenants
);

create index if not exists idx_tenant_limite_overrides_tenant on tenant_limite_overrides (tenant_id);

-- Garantia: no máximo 1 override ATIVO por tenant
create unique index if not exists uk_tenant_limite_override_ativo on tenant_limite_overrides (tenant_id) where (ativo = true);

-- Seed obrigatório: Plano PILOTO (fonte de verdade = banco)
insert into planos (
    created_at, created_by, version,
    codigo, nome, descricao, preco_mensal,
    max_instituicoes, max_unidades_atendimento, max_produtos, max_usuarios, max_qr_codes, max_dispositivos,
    permite_multi_instituicao, permite_pedidos_qr, permite_pos, permite_offline,
    ativo
) values (
    now(), 'system', 0,
    'PILOTO', 'Plano Piloto', 'Plano inicial para pilotos (90 dias, preço 0).', 0,
    1, 3, 100, 5, 10, 3,
    false, true, true, false,
    true
);
