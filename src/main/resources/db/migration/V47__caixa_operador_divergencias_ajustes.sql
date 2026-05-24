-- Prompt 42.2: Divergências, ajustes formais e justificativas financeiras (CASH/TPA)
-- Date: 2026-05-24

create table if not exists caixa_operador_divergences (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    unidade_atendimento_id bigint not null,
    turno_operacional_id bigint,
    caixa_operador_session_id bigint not null,
    operational_device_id bigint not null,
    operador_user_id bigint not null,

    status varchar(40) not null,
    type varchar(60) not null,
    severity varchar(40) not null,
    payment_method varchar(40) not null,

    expected_amount numeric(19, 2) not null,
    declared_amount numeric(19, 2) not null,
    difference_amount numeric(19, 2) not null,
    absolute_difference_amount numeric(19, 2) not null,

    reason_category varchar(80),
    description text,

    submitted_by_user_id bigint,
    submitted_at timestamp(6),

    reviewed_by_user_id bigint,
    reviewed_at timestamp(6),
    review_notes text,

    primary key (id),
    constraint fk_caixa_div_tenant foreign key (tenant_id) references tenants,
    constraint fk_caixa_div_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_caixa_div_turno foreign key (turno_operacional_id) references turnos_operacionais,
    constraint fk_caixa_div_caixa foreign key (caixa_operador_session_id) references caixa_operador_sessions,
    constraint fk_caixa_div_device foreign key (operational_device_id) references dispositivos_operacionais,
    constraint fk_caixa_div_operador_user foreign key (operador_user_id) references users,
    constraint fk_caixa_div_submitted_by_user foreign key (submitted_by_user_id) references users,
    constraint fk_caixa_div_reviewed_by_user foreign key (reviewed_by_user_id) references users,
    constraint ck_caixa_div_abs_nonneg check (absolute_difference_amount >= 0)
);

create index if not exists idx_caixa_div_tenant_status on caixa_operador_divergences (tenant_id, status);
create index if not exists idx_caixa_div_caixa on caixa_operador_divergences (tenant_id, caixa_operador_session_id);
create index if not exists idx_caixa_div_turno on caixa_operador_divergences (tenant_id, turno_operacional_id);
create index if not exists idx_caixa_div_operador on caixa_operador_divergences (tenant_id, operador_user_id);
create index if not exists idx_caixa_div_device on caixa_operador_divergences (tenant_id, operational_device_id);
create index if not exists idx_caixa_div_severity on caixa_operador_divergences (tenant_id, severity);
create index if not exists idx_caixa_div_created_at on caixa_operador_divergences (tenant_id, created_at);

create unique index if not exists uq_caixa_div_open_per_method
    on caixa_operador_divergences (tenant_id, caixa_operador_session_id, payment_method, type)
    where status in ('DRAFT', 'SUBMITTED');

create table if not exists caixa_operador_adjustments (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    caixa_operador_divergence_id bigint not null,
    caixa_operador_session_id bigint not null,

    adjustment_type varchar(80) not null,
    payment_method varchar(40) not null,
    amount numeric(19, 2) not null,
    direction varchar(60) not null,
    status varchar(40) not null,

    approved_by_user_id bigint,
    approved_at timestamp(6),

    reason text,
    evidence_reference varchar(255),

    primary key (id),
    constraint fk_caixa_adj_tenant foreign key (tenant_id) references tenants,
    constraint fk_caixa_adj_div foreign key (caixa_operador_divergence_id) references caixa_operador_divergences,
    constraint fk_caixa_adj_caixa foreign key (caixa_operador_session_id) references caixa_operador_sessions,
    constraint fk_caixa_adj_approved_by_user foreign key (approved_by_user_id) references users,
    constraint ck_caixa_adj_amount_nonneg check (amount >= 0)
);

create index if not exists idx_caixa_adj_tenant on caixa_operador_adjustments (tenant_id);
create index if not exists idx_caixa_adj_div on caixa_operador_adjustments (tenant_id, caixa_operador_divergence_id);
create index if not exists idx_caixa_adj_caixa on caixa_operador_adjustments (tenant_id, caixa_operador_session_id);
create index if not exists idx_caixa_adj_status on caixa_operador_adjustments (tenant_id, status);

