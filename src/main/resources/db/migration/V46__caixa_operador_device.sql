-- Prompt 42: Fecho de caixa por operador/device (reconciliação CASH/TPA)
-- Date: 2026-05-24

create table if not exists caixa_operador_sessions (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint not null,
    unidade_atendimento_id bigint not null,
    turno_operacional_id bigint,
    operational_device_id bigint not null,
    operador_user_id bigint not null,

    opened_by_user_id bigint not null,
    closed_by_user_id bigint,
    reviewed_by_user_id bigint,

    status varchar(40) not null,

    opened_at timestamp with time zone not null,
    closed_at timestamp with time zone,
    reviewed_at timestamp with time zone,

    expected_cash_amount numeric(19, 2) not null default 0,
    declared_cash_amount numeric(19, 2),
    cash_difference_amount numeric(19, 2),

    expected_tpa_amount numeric(19, 2) not null default 0,
    declared_tpa_amount numeric(19, 2),
    tpa_difference_amount numeric(19, 2),

    expected_manual_total_amount numeric(19, 2) not null default 0,
    declared_manual_total_amount numeric(19, 2),
    manual_difference_amount numeric(19, 2),

    expected_appypay_amount numeric(19, 2) not null default 0,

    currency varchar(10) not null default 'AOA',

    notes text,
    close_reason text,
    review_notes text,

    primary key (id),
    constraint fk_caixa_operador_tenant foreign key (tenant_id) references tenants,
    constraint fk_caixa_operador_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_caixa_operador_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_caixa_operador_turno foreign key (turno_operacional_id) references turnos_operacionais,
    constraint fk_caixa_operador_device foreign key (operational_device_id) references dispositivos_operacionais,
    constraint fk_caixa_operador_operador_user foreign key (operador_user_id) references users,
    constraint fk_caixa_operador_opened_by_user foreign key (opened_by_user_id) references users,
    constraint fk_caixa_operador_closed_by_user foreign key (closed_by_user_id) references users,
    constraint fk_caixa_operador_reviewed_by_user foreign key (reviewed_by_user_id) references users,
    constraint ck_caixa_operador_expected_cash_nonneg check (expected_cash_amount >= 0),
    constraint ck_caixa_operador_declared_cash_nonneg check (declared_cash_amount is null or declared_cash_amount >= 0),
    constraint ck_caixa_operador_expected_tpa_nonneg check (expected_tpa_amount >= 0),
    constraint ck_caixa_operador_declared_tpa_nonneg check (declared_tpa_amount is null or declared_tpa_amount >= 0),
    constraint ck_caixa_operador_expected_manual_nonneg check (expected_manual_total_amount >= 0),
    constraint ck_caixa_operador_declared_manual_nonneg check (declared_manual_total_amount is null or declared_manual_total_amount >= 0),
    constraint ck_caixa_operador_expected_appypay_nonneg check (expected_appypay_amount >= 0)
);

create index if not exists idx_caixa_operador_tenant_status on caixa_operador_sessions (tenant_id, status);
create index if not exists idx_caixa_operador_tenant_turno on caixa_operador_sessions (tenant_id, turno_operacional_id);
create index if not exists idx_caixa_operador_tenant_device_status on caixa_operador_sessions (tenant_id, operational_device_id, status);
create index if not exists idx_caixa_operador_tenant_operador_status on caixa_operador_sessions (tenant_id, operador_user_id, status);
create index if not exists idx_caixa_operador_opened_at on caixa_operador_sessions (tenant_id, opened_at);

-- Apenas um caixa OPEN por device (por tenant)
create unique index if not exists uq_caixa_operador_device_open
    on caixa_operador_sessions (tenant_id, operational_device_id)
    where status = 'OPEN';

create table if not exists caixa_operador_session_items (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    caixa_operador_session_id bigint not null,

    ordem_pagamento_id bigint not null,
    pagamento_id bigint,
    pedido_id bigint,
    sessao_consumo_id bigint,

    payment_method varchar(20) not null,
    amount numeric(19, 2) not null,
    confirmed_at timestamp with time zone,
    source varchar(40),

    primary key (id),
    constraint fk_caixa_item_tenant foreign key (tenant_id) references tenants,
    constraint fk_caixa_item_caixa foreign key (caixa_operador_session_id) references caixa_operador_sessions,
    constraint fk_caixa_item_ordem foreign key (ordem_pagamento_id) references ordens_pagamento,
    constraint fk_caixa_item_pagamento foreign key (pagamento_id) references pagamentos_gateway,
    constraint fk_caixa_item_pedido foreign key (pedido_id) references pedidos,
    constraint fk_caixa_item_sessao foreign key (sessao_consumo_id) references sessoes_consumo,
    constraint uk_caixa_item_ordem unique (tenant_id, ordem_pagamento_id),
    constraint ck_caixa_item_amount_nonneg check (amount >= 0)
);

create index if not exists idx_caixa_items_caixa on caixa_operador_session_items (tenant_id, caixa_operador_session_id);
create index if not exists idx_caixa_items_pagamento on caixa_operador_session_items (tenant_id, pagamento_id);
create index if not exists idx_caixa_items_pedido on caixa_operador_session_items (tenant_id, pedido_id);
create index if not exists idx_caixa_items_method on caixa_operador_session_items (tenant_id, payment_method);

-- Linka ordem/pagamento ao caixa (para auditoria/consulta e para freeze no close)
alter table ordens_pagamento
    add column if not exists caixa_operador_session_id bigint;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_ordem_pg_caixa_operador') then
        alter table ordens_pagamento
            add constraint fk_ordem_pg_caixa_operador
                foreign key (caixa_operador_session_id) references caixa_operador_sessions;
    end if;
end $$;

create index if not exists idx_ordem_pg_caixa_operador on ordens_pagamento (tenant_id, caixa_operador_session_id);

alter table pagamentos_gateway
    add column if not exists ordem_pagamento_id bigint;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_pagamento_ordem_pagamento') then
        alter table pagamentos_gateway
            add constraint fk_pagamento_ordem_pagamento
                foreign key (ordem_pagamento_id) references ordens_pagamento;
    end if;
end $$;

create index if not exists idx_pagamento_ordem_pagamento on pagamentos_gateway (tenant_id, ordem_pagamento_id);
