alter table users
    add column if not exists must_change_password boolean not null default false;

alter table users
    add column if not exists password_reset_required boolean not null default false;

alter table users
    add column if not exists temporary_password_expires_at timestamp;

alter table users
    add column if not exists last_password_changed_at timestamp;
