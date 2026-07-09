# Prompt 38.4 — Rollout assíncrono + progress tracking + re-run seguro

## 1) Objetivo

Evoluir o rollout de templates (Prompt 38.3) para execução **assíncrona**, com:

- criação de rollout `PENDING`;
- processamento em background (`@Scheduled`);
- itens por device+método;
- progresso incremental consultável;
- re-run seguro/idempotente;
- preservação de overrides manuais (`manualOverride`).

Sem alterar a semântica da resolução efetiva:

`TenantPaymentMethod` → `UnidadePaymentMethodPolicy` → `DevicePaymentMethodPolicy`

## 2) Preview vs Apply sync vs Submit async

- **Preview**: calcula plano, não persiste policies.
- **Apply (sync)**: mantém endpoint existente (transacional, legado `APPLIED`).
- **Submit (async)**: cria rollout + itens `PENDING` e retorna rápido; worker processa em lotes.

## 3) Estados do rollout

`PaymentMethodPolicyRolloutStatus`:

- `PENDING` → aguardando worker
- `RUNNING` → worker executando
- `COMPLETED` → todos itens concluídos sem skips/falhas
- `COMPLETED_WITH_SKIPS` → sem falhas, mas com `SKIPPED`
- `PARTIAL_FAILED` → alguns itens falharam
- `FAILED` → falha generalizada
- `CANCELLED` (reservado)
- `APPLIED` (legado do sync)

## 4) Estados dos itens

`PaymentMethodPolicyRolloutItemStatus`:

- `PENDING`
- `RUNNING`
- `CREATED`
- `UPDATED`
- `SKIPPED`
- `FAILED`

## 5) Como o worker processa

- Procura rollouts ASYNC em `PENDING/RUNNING` elegíveis.
- Faz lock otimista no rollout (`lockedAt/lockedBy`).
- Claima itens em lote com `FOR UPDATE SKIP LOCKED` (evita dupla execução).
- Processa cada item em transação própria (`REQUIRES_NEW`):
  - decide `CREATE/UPDATE/SKIP`;
  - respeita `overwriteMode`;
  - protege `manualOverride` (ex.: `OVERWRITE_ONLY_TEMPLATE_MANAGED`).
- Atualiza contadores globais e finaliza status.

## 6) Progresso

`progressPercent = processedItems / totalItems * 100`

`processedItems = succeededItems + skippedItems + failedItems`

## 7) batch-size

Controlado por `consuma.financeiro.payment-policy-rollout.batch-size` (default 100).

## 8) Idempotência do submit

`Idempotency-Key` (header) cria constraint única por `(tenantId, idempotencyKey)`.

- mesma key + mesmo payload → retorna rollout existente;
- mesma key + payload diferente → erro.

## 9) Re-run seguro

`POST /tenant/payment-policy-rollouts/{rolloutId}/rerun`

- por padrão, reprocessa apenas itens `FAILED/PENDING`;
- não reprocessa `CREATED/UPDATED`;
- preserva manual overrides.

## 10) Overwrite modes no async

Mesma semântica do sync:

- `SKIP_EXISTING`
- `OVERWRITE_EXISTING`
- `OVERWRITE_ONLY_TEMPLATE_MANAGED` (não pisa em `manualOverride=true`)

## 11) Limitações (MVP)

- Sem fila externa (Kafka/Rabbit/SQS)
- Sem WebSocket/SSE
- Sem UI
- Sem cancelamento avançado (status `CANCELLED` reservado)
- Multi-worker avançado/distribuído não coberto além de locks no DB

## 12) Próximo passo recomendado

- Implementar cancelamento controlado e “drain” de itens `RUNNING`
- Backoff por item (nextRetryAt por item) para evitar retry agressivo
- Auditoria agregada por batch para reduzir volume

