-- Prompt 39: Consumo identificado por telefone + OTP (tenant-aware)

create table if not exists cliente_consumo (
    id bigserial primary key,
    tenant_id bigint not null,
    telefone varchar(30) not null,
    telefone_normalizado varchar(30) not null,
    nome varchar(120) null,
    status varchar(30) not null,
    telefone_verificado boolean not null default false,
    primeiro_verificado_em timestamptz null,
    ultimo_verificado_em timestamptz null,
    ultimo_acesso_em timestamptz null,
    metadata_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz null,
    constraint uk_cliente_consumo_tenant_phone unique (tenant_id, telefone_normalizado)
);

create index if not exists idx_cliente_consumo_tenant_status on cliente_consumo (tenant_id, status);
create index if not exists idx_cliente_consumo_tenant_phone_norm on cliente_consumo (tenant_id, telefone_normalizado);

alter table cliente_consumo
    add constraint fk_cliente_consumo_tenant
        foreign key (tenant_id) references tenants (id);

create table if not exists telefone_otp_challenge (
    id bigserial primary key,
    tenant_id bigint not null,
    telefone_normalizado varchar(30) not null,
    purpose varchar(50) not null,
    otp_hash varchar(255) not null,
    status varchar(30) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    attempts integer not null default 0,
    max_attempts integer not null default 5,
    resend_count integer not null default 0,
    last_sent_at timestamptz not null,
    client_ip varchar(100) null,
    user_agent varchar(255) null,
    sessao_consumo_id bigint null,
    created_at timestamptz not null,
    updated_at timestamptz null
);

create index if not exists idx_otp_challenge_tenant_phone_status on telefone_otp_challenge (tenant_id, telefone_normalizado, status);
create index if not exists idx_otp_challenge_expires_at on telefone_otp_challenge (expires_at);
create index if not exists idx_otp_challenge_tenant_ip_created on telefone_otp_challenge (tenant_id, client_ip, created_at);

alter table telefone_otp_challenge
    add constraint fk_otp_challenge_tenant
        foreign key (tenant_id) references tenants (id);

alter table telefone_otp_challenge
    add constraint fk_otp_challenge_sessao
        foreign key (sessao_consumo_id) references sessoes_consumo (id);

alter table sessoes_consumo
    add column if not exists cliente_consumo_id bigint null;

alter table sessoes_consumo
    add column if not exists telefone_identificado varchar(30) null;

alter table sessoes_consumo
    add column if not exists telefone_identificado_em timestamptz null;

alter table sessoes_consumo
    add column if not exists identificacao_status varchar(30) null;

alter table sessoes_consumo
    add column if not exists identificado_por_otp boolean not null default false;

create index if not exists idx_sessao_tenant_phone_identificado_status
    on sessoes_consumo (tenant_id, telefone_identificado, status);

create index if not exists idx_sessao_tenant_cliente_consumo_status
    on sessoes_consumo (tenant_id, cliente_consumo_id, status);

alter table sessoes_consumo
    add constraint fk_sessao_cliente_consumo
        foreign key (cliente_consumo_id) references cliente_consumo (id);

