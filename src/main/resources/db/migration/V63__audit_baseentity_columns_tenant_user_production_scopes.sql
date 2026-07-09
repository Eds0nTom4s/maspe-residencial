-- Ensure BaseEntity audit/version columns exist for tenant_user_production_scopes
-- Required for Hibernate schema validation (BaseEntity fields).

alter table if exists tenant_user_production_scopes
    add column if not exists created_at timestamp with time zone not null default now();

alter table if exists tenant_user_production_scopes
    add column if not exists updated_at timestamp with time zone null;

alter table if exists tenant_user_production_scopes
    add column if not exists created_by varchar(100);

alter table if exists tenant_user_production_scopes
    add column if not exists modified_by varchar(100);

alter table if exists tenant_user_production_scopes
    add column if not exists version bigint not null default 0;

