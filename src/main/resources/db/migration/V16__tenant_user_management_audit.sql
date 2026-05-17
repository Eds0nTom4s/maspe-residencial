-- Prompt 19: Gestão de usuários do tenant + auditoria operacional mínima

create table tenant_audit_logs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    actor_user_id bigint not null,
    target_user_id bigint,

    entity_type varchar(60) not null,
    entity_id bigint,

    action varchar(60) not null,
    status varchar(20) not null,

    ip varchar(64),
    user_agent varchar(255),

    message varchar(500),
    metadata_json text,

    primary key (id),
    constraint fk_tenant_audit_logs_tenant foreign key (tenant_id) references tenants (id)
);

create index idx_tenant_audit_logs_tenant on tenant_audit_logs (tenant_id);
create index idx_tenant_audit_logs_actor on tenant_audit_logs (actor_user_id);
create index idx_tenant_audit_logs_target on tenant_audit_logs (target_user_id);
create index idx_tenant_audit_logs_action on tenant_audit_logs (action);
create index idx_tenant_audit_logs_created_at on tenant_audit_logs (created_at);
create index idx_tenant_audit_logs_tenant_created_at on tenant_audit_logs (tenant_id, created_at);

