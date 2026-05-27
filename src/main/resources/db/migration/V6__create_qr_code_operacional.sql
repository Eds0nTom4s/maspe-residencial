-- CONSUMA Tenant Core (V6)
-- Introduces QR Code Operacional tenant-safe (public token, non-enumerable).
-- Date: 2026-05-15
-- IMPORTANT: Does NOT replace legacy qr_code_tokens yet.

create table if not exists qr_codes_operacionais (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint not null,
    unidade_atendimento_id bigint,
    mesa_id bigint,

    token varchar(64) not null,
    nome varchar(120),
    tipo varchar(30) not null,
    ativo boolean not null,
    revogado boolean not null,
    revogado_em timestamp(6),

    primary key (id),
    constraint uk_qr_codes_operacionais_token unique (token),
    constraint fk_qr_operacional_tenant foreign key (tenant_id) references tenants,
    constraint fk_qr_operacional_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_qr_operacional_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_qr_operacional_mesa foreign key (mesa_id) references mesas
);

create index if not exists idx_qr_operacional_tenant on qr_codes_operacionais (tenant_id);
create index if not exists idx_qr_operacional_instituicao on qr_codes_operacionais (instituicao_id);
create index if not exists idx_qr_operacional_unidade on qr_codes_operacionais (unidade_atendimento_id);
create index if not exists idx_qr_operacional_tenant_ativo on qr_codes_operacionais (tenant_id, ativo);
create index if not exists idx_qr_operacional_token on qr_codes_operacionais (token);
