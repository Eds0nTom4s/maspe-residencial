alter table caixa_operador_sessions
    alter column operational_device_id drop not null;

alter table caixa_operador_sessions
    add column if not exists channel varchar(20) not null default 'DEVICE_POS';

create unique index if not exists uq_caixa_operador_web_open
    on caixa_operador_sessions (tenant_id, operador_user_id)
    where status = 'OPEN' and channel = 'WEB_PDV';

alter table caixa_operador_sessions
    add constraint ck_caixa_operador_channel_device
    check (
        (channel = 'DEVICE_POS' and operational_device_id is not null)
        or (channel = 'WEB_PDV' and operational_device_id is null)
    );

alter table ordens_pagamento
    add column if not exists metodo_confirmado varchar(20),
    add column if not exists valor_recebido numeric(19, 2),
    add column if not exists troco numeric(19, 2);

alter table ordens_pagamento
    add constraint ck_ordem_pg_valor_recebido_nonneg
    check (valor_recebido is null or valor_recebido >= 0),
    add constraint ck_ordem_pg_troco_nonneg
    check (troco is null or troco >= 0);
