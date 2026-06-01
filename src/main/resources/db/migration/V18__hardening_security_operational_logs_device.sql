-- Prompt 21: Hardening transversal
-- 1) Tenant token staleness mitigation (tenant_user_access_versions)
-- 2) Device auth hardening (token_version + device_event_logs)

-- ---------------------------------------------------------------------------
-- 1) Tenant access version (invalidate stale tenant-scoped JWT after changes)
-- ---------------------------------------------------------------------------

create table tenant_user_access_versions (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    user_id bigint not null,
    access_version integer not null,
    permissions_updated_at timestamp(6) not null,

    primary key (id),
    constraint fk_tuav_tenant foreign key (tenant_id) references tenants (id),
    constraint fk_tuav_user foreign key (user_id) references users (id),
    constraint uk_tuav_tenant_user unique (tenant_id, user_id)
);

create index idx_tuav_tenant_user on tenant_user_access_versions (tenant_id, user_id);
create index idx_tuav_user on tenant_user_access_versions (user_id);
create index idx_tuav_permissions_updated_at on tenant_user_access_versions (permissions_updated_at);

-- Backfill: qualquer par (tenant_id, user_id) existente em tenant_users recebe version=1
insert into tenant_user_access_versions (
    created_at,
    tenant_id,
    user_id,
    access_version,
    permissions_updated_at
)
select
    now(),
    tu.tenant_id,
    tu.user_id,
    1,
    now()
from tenant_users tu
group by tu.tenant_id, tu.user_id
on conflict (tenant_id, user_id) do nothing;


-- ---------------------------------------------------------------------------
-- 2) Device auth hardening
-- ---------------------------------------------------------------------------

alter table dispositivos_operacionais
    add column if not exists token_version integer not null default 1;

alter table dispositivos_operacionais
    add column if not exists last_token_rotation_at timestamp(6);

alter table dispositivos_operacionais
    add column if not exists last_auth_at timestamp(6);

alter table dispositivos_operacionais
    add column if not exists last_auth_failure_at timestamp(6);

create table device_event_logs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    dispositivo_id bigint not null,

    event_type varchar(40) not null,
    status varchar(20) not null,
    message varchar(500),
    metadata_json text,
    ip varchar(64),
    user_agent varchar(255),

    primary key (id),
    constraint fk_device_event_tenant foreign key (tenant_id) references tenants (id),
    constraint fk_device_event_device foreign key (dispositivo_id) references dispositivos_operacionais (id)
);

create index idx_device_event_tenant on device_event_logs (tenant_id);
create index idx_device_event_tenant_created_at on device_event_logs (tenant_id, created_at);
create index idx_device_event_device on device_event_logs (dispositivo_id);
create index idx_device_event_event_type on device_event_logs (event_type);

alter table device_event_logs
    add column if not exists created_by varchar(100);
alter table device_event_logs
    add column if not exists modified_by varchar(100);
