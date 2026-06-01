-- Prompt 18: Dispositivos Operacionais (POS/KDS/etc.) tenant-aware
-- Objetivo: Identidade de dispositivo, ativação por código curto (hash), token opaco (hash),
--           heartbeat e vinculação a Tenant/Instituicao/UnidadeAtendimento.

create table if not exists dispositivos_operacionais (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint not null,
    unidade_atendimento_id bigint,

    nome varchar(120) not null,
    codigo varchar(60) not null,
    tipo varchar(30) not null,
    status varchar(30) not null,

    activation_code_hash varchar(64),
    activation_code_expires_at timestamp(6),

    device_token_hash varchar(64),
    device_token_issued_at timestamp(6),
    device_token_revoked_at timestamp(6),

    ultimo_heartbeat_em timestamp(6),
    ultimo_ip varchar(64),
    user_agent varchar(255),

    app_version varchar(40),
    platform varchar(30),
    modelo_dispositivo varchar(80),
    fabricante varchar(80),
    serial_hash varchar(64),

    ativado_em timestamp(6),
    revogado_em timestamp(6),

    primary key (id),
    constraint fk_dispositivo_tenant foreign key (tenant_id) references tenants,
    constraint fk_dispositivo_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_dispositivo_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint uk_dispositivo_tenant_codigo unique (tenant_id, codigo)
);

create index if not exists idx_dispositivo_tenant on dispositivos_operacionais (tenant_id);
create index if not exists idx_dispositivo_tenant_status on dispositivos_operacionais (tenant_id, status);
create index if not exists idx_dispositivo_tenant_instituicao on dispositivos_operacionais (tenant_id, instituicao_id);
create index if not exists idx_dispositivo_tenant_unidade on dispositivos_operacionais (tenant_id, unidade_atendimento_id);
create index if not exists idx_dispositivo_device_token_hash on dispositivos_operacionais (device_token_hash);
create index if not exists idx_dispositivo_activation_code_hash on dispositivos_operacionais (activation_code_hash);
create index if not exists idx_dispositivo_ultimo_heartbeat on dispositivos_operacionais (ultimo_heartbeat_em);

-- Baseline V1 já cria a tabela/índices acima, mas não garantia unicidade por (tenant_id, codigo).
-- Em ambientes onde a tabela já existe, criamos o índice único sem falhar.
create unique index if not exists uk_dispositivo_tenant_codigo
    on dispositivos_operacionais (tenant_id, codigo);
