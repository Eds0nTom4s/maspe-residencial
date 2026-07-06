-- Prompt PAYMENT_ORDER_EXPIRATION_001: consulta segura da ordem de pagamento do pedido.
-- A tabela ordens_pagamento ja existe desde V24; esta migration apenas reforca
-- o acesso por pedido/status/expiracao sem criar ordens retroativas.

create index if not exists idx_ordem_pg_pedido_status_expires
    on ordens_pagamento (tenant_id, pedido_id, status, expires_at);
