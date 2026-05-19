-- Prompt 29: Turnos Operacionais + Checklists de Abertura/Fecho
-- Objetivo: disciplina operacional (abertura/fecho), checklist obrigatório, pré-fecho e resumo por período.

create table turnos_operacionais (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint not null,
    unidade_atendimento_id bigint not null,

    aberto_por_user_id bigint not null,
    fechado_por_user_id bigint,
    dispositivo_abertura_id bigint,
    dispositivo_fecho_id bigint,

    status varchar(20) not null,
    tipo varchar(20) not null,
    nome varchar(120) not null,

    aberto_em timestamp(6) not null,
    fechado_em timestamp(6),

    observacao_abertura varchar(500),
    observacao_fecho varchar(500),
    resumo_json text,

    primary key (id),
    constraint fk_turno_tenant foreign key (tenant_id) references tenants,
    constraint fk_turno_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_turno_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_turno_aberto_por foreign key (aberto_por_user_id) references users,
    constraint fk_turno_fechado_por foreign key (fechado_por_user_id) references users,
    constraint fk_turno_dispositivo_abertura foreign key (dispositivo_abertura_id) references dispositivos_operacionais,
    constraint fk_turno_dispositivo_fecho foreign key (dispositivo_fecho_id) references dispositivos_operacionais
);

create index idx_turno_tenant on turnos_operacionais (tenant_id);
create index idx_turno_tenant_status on turnos_operacionais (tenant_id, status);
create index idx_turno_tenant_inst_ua on turnos_operacionais (tenant_id, instituicao_id, unidade_atendimento_id);
create index idx_turno_tenant_aberto_em on turnos_operacionais (tenant_id, aberto_em);

-- Apenas 1 turno ABERTO/EM_FECHO por tenant+instituicao+unidade_atendimento
create unique index ux_turno_aberto_tenant_inst_ua
    on turnos_operacionais (tenant_id, instituicao_id, unidade_atendimento_id)
    where status in ('ABERTO', 'EM_FECHO');

create table checklist_operacional_templates (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint,
    tipo varchar(20) not null,
    nome varchar(120) not null,
    ativo boolean not null default true,
    escopo varchar(30) not null,

    primary key (id),
    constraint fk_checklist_template_tenant foreign key (tenant_id) references tenants
);

create index idx_checklist_template_tenant on checklist_operacional_templates (tenant_id);
create index idx_checklist_template_tipo on checklist_operacional_templates (tipo);
create index idx_checklist_template_ativo on checklist_operacional_templates (ativo);

create table checklist_operacional_item_templates (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    template_id bigint not null,
    codigo varchar(60) not null,
    descricao varchar(255) not null,
    obrigatorio boolean not null default true,
    ordem integer not null default 0,
    tipo_resposta varchar(20) not null,
    ativo boolean not null default true,

    primary key (id),
    constraint fk_checklist_item_template_template foreign key (template_id) references checklist_operacional_templates
);

create index idx_checklist_item_template_template on checklist_operacional_item_templates (template_id);
create index idx_checklist_item_template_codigo on checklist_operacional_item_templates (codigo);
create index idx_checklist_item_template_ativo on checklist_operacional_item_templates (ativo);

create table checklist_operacional_runs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    turno_id bigint not null,
    template_id bigint not null,
    tipo varchar(20) not null,
    status varchar(20) not null,
    executado_por_user_id bigint not null,
    dispositivo_id bigint,
    iniciado_em timestamp(6),
    concluido_em timestamp(6),

    primary key (id),
    constraint fk_checklist_run_tenant foreign key (tenant_id) references tenants,
    constraint fk_checklist_run_turno foreign key (turno_id) references turnos_operacionais,
    constraint fk_checklist_run_template foreign key (template_id) references checklist_operacional_templates,
    constraint fk_checklist_run_executado_por foreign key (executado_por_user_id) references users,
    constraint fk_checklist_run_dispositivo foreign key (dispositivo_id) references dispositivos_operacionais
);

create index idx_checklist_run_tenant on checklist_operacional_runs (tenant_id);
create index idx_checklist_run_tenant_turno on checklist_operacional_runs (tenant_id, turno_id);
create index idx_checklist_run_tipo on checklist_operacional_runs (tipo);
create index idx_checklist_run_status on checklist_operacional_runs (status);

create table checklist_operacional_item_runs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    run_id bigint not null,
    item_template_id bigint,
    codigo varchar(60) not null,
    descricao varchar(255) not null,
    obrigatorio boolean not null default true,
    tipo_resposta varchar(20) not null,

    valor_boolean boolean,
    valor_texto varchar(1000),
    valor_numero numeric(18, 4),

    status varchar(20) not null,
    observacao varchar(500),
    respondido_em timestamp(6),

    primary key (id),
    constraint fk_checklist_item_run_run foreign key (run_id) references checklist_operacional_runs,
    constraint fk_checklist_item_run_template foreign key (item_template_id) references checklist_operacional_item_templates
);

create index idx_checklist_item_run_run on checklist_operacional_item_runs (run_id);
create index idx_checklist_item_run_codigo on checklist_operacional_item_runs (codigo);
create index idx_checklist_item_run_status on checklist_operacional_item_runs (status);

-- Pedido -> TurnoOperacional (nullable nesta fase)
alter table pedidos add column turno_operacional_id bigint;
alter table pedidos add constraint fk_pedido_turno_operacional foreign key (turno_operacional_id) references turnos_operacionais;
create index idx_pedido_tenant_turno_operacional on pedidos (tenant_id, turno_operacional_id);

-- OperationalEventLog -> TurnoOperacional (nullable)
alter table operational_event_logs add column turno_id bigint;
alter table operational_event_logs add constraint fk_operational_event_turno foreign key (turno_id) references turnos_operacionais;
create index idx_operational_event_tenant_turno on operational_event_logs (tenant_id, turno_id);

