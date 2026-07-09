-- Prompt 20: OperationalEventLog (event log operacional tenant-aware) + status transitions auditáveis

create table if not exists operational_event_logs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    instituicao_id bigint,
    unidade_atendimento_id bigint,

    pedido_id bigint,
    sub_pedido_id bigint,
    item_pedido_id bigint,
    mesa_id bigint,

    device_id bigint,
    actor_user_id bigint,
    actor_type varchar(30) not null,

    event_type varchar(60) not null,
    entity_type varchar(30) not null,
    entity_id bigint not null,

    status_anterior varchar(60),
    status_novo varchar(60),

    origem varchar(40) not null,
    motivo varchar(500),
    metadata_json text,

    ip varchar(64),
    user_agent varchar(255),

    primary key (id),
    constraint fk_operational_event_tenant foreign key (tenant_id) references tenants (id),
    constraint fk_operational_event_instituicao foreign key (instituicao_id) references instituicoes (id),
    constraint fk_operational_event_unidade foreign key (unidade_atendimento_id) references unidades_atendimento (id),
    constraint fk_operational_event_pedido foreign key (pedido_id) references pedidos (id),
    constraint fk_operational_event_sub_pedido foreign key (sub_pedido_id) references sub_pedidos (id),
    constraint fk_operational_event_item_pedido foreign key (item_pedido_id) references itens_pedido (id),
    constraint fk_operational_event_mesa foreign key (mesa_id) references mesas (id),
    constraint fk_operational_event_device foreign key (device_id) references dispositivos_operacionais (id),
    constraint fk_operational_event_actor_user foreign key (actor_user_id) references users (id)
);

create index if not exists idx_operational_event_tenant on operational_event_logs (tenant_id);
create index if not exists idx_operational_event_tenant_created_at on operational_event_logs (tenant_id, created_at);
create index if not exists idx_operational_event_tenant_pedido on operational_event_logs (tenant_id, pedido_id);
create index if not exists idx_operational_event_tenant_sub_pedido on operational_event_logs (tenant_id, sub_pedido_id);
create index if not exists idx_operational_event_tenant_event_type on operational_event_logs (tenant_id, event_type);
create index if not exists idx_operational_event_tenant_actor_user on operational_event_logs (tenant_id, actor_user_id);
create index if not exists idx_operational_event_tenant_device on operational_event_logs (tenant_id, device_id);
