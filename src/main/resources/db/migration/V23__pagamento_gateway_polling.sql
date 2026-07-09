-- Prompt 32: Polling ativo do gateway para pagamentos pendentes (rede de segurança do callback)

alter table pagamentos_gateway
    add column if not exists polling_enabled boolean not null default true,
    add column if not exists polling_attempts integer not null default 0,
    add column if not exists last_polling_attempt_at timestamp(6),
    add column if not exists next_polling_attempt_at timestamp(6),
    add column if not exists polling_status varchar(50),
    add column if not exists polling_last_error_code varchar(100),
    add column if not exists polling_last_error_message text,
    add column if not exists gateway_status_last_checked_at timestamp(6),
    add column if not exists gateway_status_raw text,
    add column if not exists expires_at timestamp(6);

create index if not exists idx_pagamento_polling_next_attempt
    on pagamentos_gateway (status, next_polling_attempt_at);

create index if not exists idx_pagamento_polling_enabled_next_attempt
    on pagamentos_gateway (polling_enabled, next_polling_attempt_at);

create index if not exists idx_pagamento_polling_status
    on pagamentos_gateway (tenant_id, polling_status);

