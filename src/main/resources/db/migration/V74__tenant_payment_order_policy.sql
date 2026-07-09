ALTER TABLE tenant_operacao_policies
    ADD COLUMN IF NOT EXISTS pagamento_obrigatorio_antes_do_pedido boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS permitir_pedido_sem_pagamento boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS permitir_pos_pago boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS permitir_cash boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS permitir_pagamento_na_entrega boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS tempo_expiracao_pedido_pendente_pagamento_minutos integer NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS comportamento_pedido_nao_pago varchar(80) NOT NULL DEFAULT 'CRIAR_PENDENTE';

