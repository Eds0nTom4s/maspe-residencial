# Prompt 42 — Fecho de caixa por operador/device (CASH/TPA)

## Objetivo
Adicionar uma camada operacional de reconciliação física para pagamentos manuais (CASH/TPA), permitindo:
- rastrear **qual operador** recebeu valores;
- em **qual device/POS**, **qual unidade** e **qual turno**;
- calcular **esperado vs declarado** e **diferenças**;
- congelar um **snapshot (items)** do que compôs o caixa no momento do fecho;
- permitir **revisão** (REVIEWED/DISPUTED) por roles financeiras do tenant;
- manter AppyPay e snapshot/evidence bundle existentes **inalterados** (sem redesign).

## Conceitos
### `CaixaOperadorSession`
Sessão operacional de caixa por **operador + device** (e opcionalmente turno).

Status:
- `OPEN`
- `CLOSED`
- `REVIEWED`
- `DISPUTED`
- `CANCELLED` (reservado)

### `CaixaOperadorSessionItem`
Linhas congeladas no fecho do caixa, derivadas das `OrdemPagamento` manuais confirmadas (CASH/TPA).

Regras:
- 1 item por `ordem_pagamento_id` (idempotente via constraint unique).
- Não depende de janela temporal; depende do vínculo explícito `ordem_pagamento.caixa_operador_session_id`.

## Modelo de dados
Migration:
- `src/main/resources/db/migration/V46__caixa_operador_device.sql`

Tabelas:
- `caixa_operador_sessions`
- `caixa_operador_session_items`

Campos adicionados:
- `ordens_pagamento.caixa_operador_session_id`
- `pagamentos_gateway.ordem_pagamento_id`

## Capabilities
Adicionadas em `DeviceCapability`:
- `OPEN_OPERATOR_CASH_SESSION`
- `CLOSE_OPERATOR_CASH_SESSION`
- `VIEW_OPERATOR_CASH_SESSION`
- `VIEW_OPERATOR_CASH_SESSION_ITEMS`
- `REVIEW_OPERATOR_CASH_SESSION` (reservado para device; revisão é tenant/admin)

Defaults (`DeviceCapabilityDefaults`):
- `POS_CAIXA`: open/close/view (+ items)
- `POS_ATENDIMENTO`: view
- `KDS/QUIOSQUE/DISPLAY/ADMIN_TERMINAL`: sem defaults (configuração explícita via tenant/admin)

## Endpoints
### Device
- `POST /device/caixa-operador/open`
  - cria `CaixaOperadorSession` em `OPEN`.
  - exige capability `OPEN_OPERATOR_CASH_SESSION`.
  - requer `operadorUserId` (valida pertença ao tenant).

- `GET /device/caixa-operador/current`
  - retorna o caixa `OPEN` do device.
  - exige capability `VIEW_OPERATOR_CASH_SESSION`.

- `POST /device/caixa-operador/{caixaId}/close`
  - calcula esperados (CASH/TPA), diferenças e fecha o caixa.
  - gera snapshot `caixa_operador_session_items`.
  - exige capability `CLOSE_OPERATOR_CASH_SESSION`.

### Tenant/Admin
- `GET /tenant/caixa-operador`
  - lista caixas por tenant com filtros (status/unidade/turno/device/operador) + paginação.
  - roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`.

- `GET /tenant/caixa-operador/{caixaId}`
  - detalhe do caixa.

- `GET /tenant/caixa-operador/{caixaId}/items`
  - lista items congelados do caixa.

- `POST /tenant/caixa-operador/{caixaId}/review`
  - marca `REVIEWED` ou `DISPUTED`.
  - roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`.

## Integração com confirmação manual (CASH/TPA)
No fluxo de confirmação manual (`/device/ordens-pagamento/{ordemId}/confirmar-manual`):
- passou a exigir **caixa OPEN** para o device (`CaixaOperadorSession`).
- ao confirmar, a `OrdemPagamento` é vinculada ao caixa (`caixa_operador_session_id`).
- o `Pagamento` manual criado passa a referenciar a `OrdemPagamento` (`pagamentos_gateway.ordem_pagamento_id`) para troubleshooting.

AppyPay:
- permanece **online-only** e **não exige** caixa OPEN.

## Auditoria
Eventos adicionados em `OperationalEventType` (metadata sanitizada):
- `CAIXA_OPERADOR_SESSION_OPENED`
- `CAIXA_OPERADOR_SESSION_CLOSED`
- `CAIXA_OPERADOR_SESSION_REVIEWED`
- `CAIXA_OPERADOR_SESSION_DISPUTED`
- `CAIXA_OPERADOR_SESSION_REQUIRED_BUT_MISSING`
- `CAIXA_OPERADOR_SESSION_CLOSE_FAILED`
- `CAIXA_OPERADOR_SESSION_REVIEW_FAILED`

Regras:
- não registrar tokens/secrets/OTP/telefone completo.

## Limitações (MVP)
- Não implementa UI.
- Não implementa abertura/fecho offline.
- Não integra ainda o resumo de caixas ao Evidence Bundle consolidado (dívida: `P42-DEBT-1`).
- Não trata multi-moeda.
- Não permite “reabrir” caixa; correções devem ocorrer via revisão/disputa e processos posteriores.

## Próximo passo recomendado
**Prompt 42.1**: integrar um resumo de `CaixaOperadorSession`/diferenças no relatório/snapshot/Evidence Bundle do turno (sem quebrar WORM).

