-- Prompt 41.1 (Sandbox): Provisionamento por template pode criar owner user com username=email.
-- Emails válidos podem exceder 50 chars, causando DataIntegrityViolation (SQLState 22001).
-- Ajuste seguro: aumentar limite do username para suportar emails comuns.

alter table users
    alter column username type varchar(120);

