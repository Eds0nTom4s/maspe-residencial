-- Prompt 36: Gestão de consumo físico + ordem de pagamento manual (CASH/TPA)

-- 1) FundoConsumo: bloquear/desbloquear (freeze operacional do saldo)
alter table fundos_consumo
    add column if not exists bloqueado boolean not null default false;

alter table fundos_consumo
    add column if not exists bloqueado_em timestamp(6);

alter table fundos_consumo
    add column if not exists bloqueado_motivo varchar(500);

create index if not exists idx_fundo_bloqueado on fundos_consumo (bloqueado);

-- 2) OrdemPagamento (manual / gateway-agnostic)
create table if not exists ordens_pagamento (
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

    tipo varchar(30) not null,
    status varchar(30) not null,
    metodo_solicitado varchar(20) not null,

    valor numeric(19,2) not null,
    moeda varchar(3) not null default 'AOA',

    pedido_id bigint,
    sessao_consumo_id bigint,
    fundo_consumo_id bigint,

    token_qr varchar(80) not null,
    codigo_curto varchar(20),
    expires_at timestamp(6),

    criado_por_origem varchar(40) not null,

    confirmado_por_device_id bigint,
    confirmado_por_user_id bigint,
    confirmado_em timestamp(6),
    referencia_operador varchar(200),
    observacao varchar(500),

    primary key (id),
    constraint fk_ordem_pg_tenant foreign key (tenant_id) references tenants,
    constraint fk_ordem_pg_instituicao foreign key (instituicao_id) references instituicoes,
    constraint fk_ordem_pg_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_ordem_pg_turno foreign key (turno_operacional_id) references turnos_operacionais,
    constraint fk_ordem_pg_pedido foreign key (pedido_id) references pedidos,
    constraint fk_ordem_pg_sessao foreign key (sessao_consumo_id) references sessoes_consumo,
    constraint fk_ordem_pg_fundo foreign key (fundo_consumo_id) references fundos_consumo,
    constraint fk_ordem_pg_device foreign key (confirmado_por_device_id) references dispositivos_operacionais,
    constraint fk_ordem_pg_user foreign key (confirmado_por_user_id) references users,
    constraint uk_ordem_pg_token unique (token_qr)
);

create index if not exists idx_ordem_pg_tenant on ordens_pagamento (tenant_id);
create index if not exists idx_ordem_pg_status on ordens_pagamento (tenant_id, status);
create index if not exists idx_ordem_pg_tipo on ordens_pagamento (tenant_id, tipo);
create index if not exists idx_ordem_pg_pedido on ordens_pagamento (tenant_id, pedido_id);
create index if not exists idx_ordem_pg_sessao on ordens_pagamento (tenant_id, sessao_consumo_id);
create index if not exists idx_ordem_pg_fundo on ordens_pagamento (tenant_id, fundo_consumo_id);
create index if not exists idx_ordem_pg_turno on ordens_pagamento (tenant_id, turno_operacional_id);
create index if not exists idx_ordem_pg_codigo_curto on ordens_pagamento (tenant_id, codigo_curto);

