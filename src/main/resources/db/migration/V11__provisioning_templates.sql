-- CONSUMA Tenant Core (V11)
-- Provisioning templates (manual administered provisioning by PLATFORM_ADMIN)
-- Date: 2026-05-16

create table if not exists provisioning_templates (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    codigo varchar(60) not null,
    nome varchar(120) not null,
    descricao varchar(500),
    tipo_tenant varchar(40) not null,
    ativo boolean not null,
    configuracao_json text,

    primary key (id)
);

create unique index if not exists uk_provisioning_template_codigo on provisioning_templates (codigo);
create index if not exists idx_provisioning_template_ativo on provisioning_templates (ativo);
create index if not exists idx_provisioning_template_tipo_tenant on provisioning_templates (tipo_tenant);

-- Seeds (idempotent)
insert into provisioning_templates (created_at, updated_at, codigo, nome, descricao, tipo_tenant, ativo, configuracao_json)
select now(), now(),
       'VENDEDOR_RUA',
       'Vendedor de Rua',
       'Balcão simples (sem mesas) com QR principal.',
       'VENDEDOR_RUA',
       true,
       $$
       {
         "unidadeAtendimentoDefault": { "nome": "Balcão Principal", "tipo": "BAR" },
         "categoriaDefault": { "nome": "Geral", "slug": "geral" },
         "qrPrincipal": { "nome": "Consuma aqui", "tipo": "BALCAO" },
         "criarMesas": false,
         "quantidadeMesas": 0
       }
       $$
where not exists (select 1 from provisioning_templates where codigo = 'VENDEDOR_RUA');

insert into provisioning_templates (created_at, updated_at, codigo, nome, descricao, tipo_tenant, ativo, configuracao_json)
select now(), now(),
       'RESTAURANTE_SIMPLES',
       'Restaurante Simples',
       'Restaurante com unidade padrão e QR principal (mesas opcionais futuras).',
       'RESTAURANTE',
       true,
       $$
       {
         "unidadeAtendimentoDefault": { "nome": "Salão Principal", "tipo": "RESTAURANTE" },
         "categoriaDefault": { "nome": "Geral", "slug": "geral" },
         "qrPrincipal": { "nome": "QR Salão Principal", "tipo": "UNIDADE_ATENDIMENTO" },
         "criarMesas": false,
         "quantidadeMesas": 0
       }
       $$
where not exists (select 1 from provisioning_templates where codigo = 'RESTAURANTE_SIMPLES');

insert into provisioning_templates (created_at, updated_at, codigo, nome, descricao, tipo_tenant, ativo, configuracao_json)
select now(), now(),
       'BAR',
       'Bar',
       'Bar com balcão principal e QR principal.',
       'BAR',
       true,
       $$
       {
         "unidadeAtendimentoDefault": { "nome": "Balcão Principal", "tipo": "BAR" },
         "categoriaDefault": { "nome": "Geral", "slug": "geral" },
         "qrPrincipal": { "nome": "QR Balcão", "tipo": "BALCAO" },
         "criarMesas": false,
         "quantidadeMesas": 0
       }
       $$
where not exists (select 1 from provisioning_templates where codigo = 'BAR');

insert into provisioning_templates (created_at, updated_at, codigo, nome, descricao, tipo_tenant, ativo, configuracao_json)
select now(), now(),
       'LOJA',
       'Loja',
       'Operação simples (sem mesas) com QR geral.',
       'LOJA',
       true,
       $$
       {
         "unidadeAtendimentoDefault": { "nome": "Atendimento", "tipo": "CAFETERIA" },
         "categoriaDefault": { "nome": "Geral", "slug": "geral" },
         "qrPrincipal": { "nome": "QR Loja", "tipo": "TENANT_GERAL" },
         "criarMesas": false,
         "quantidadeMesas": 0
       }
       $$
where not exists (select 1 from provisioning_templates where codigo = 'LOJA');

insert into provisioning_templates (created_at, updated_at, codigo, nome, descricao, tipo_tenant, ativo, configuracao_json)
select now(), now(),
       'EVENTO',
       'Evento',
       'Evento com QR principal para balcão/ponto de venda.',
       'EVENTO',
       true,
       $$
       {
         "unidadeAtendimentoDefault": { "nome": "Balcão Evento", "tipo": "EVENTO" },
         "categoriaDefault": { "nome": "Geral", "slug": "geral" },
         "qrPrincipal": { "nome": "QR Evento", "tipo": "EVENTO" },
         "criarMesas": false,
         "quantidadeMesas": 0
       }
       $$
where not exists (select 1 from provisioning_templates where codigo = 'EVENTO');

