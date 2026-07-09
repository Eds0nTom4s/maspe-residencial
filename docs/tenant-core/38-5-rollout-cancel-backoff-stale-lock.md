# Prompt 38.5 — Cancelamento + backoff por item + stale lock recovery + rate-limit de auditoria

## 1) Objetivo

Endurecer o rollout assíncrono (Prompt 38.4) para operação real:

- cancelamento controlado;
- backoff por item (`nextRetryAt`);
- recuperação de stale locks (rollout e itens RUNNING);
- rate-limit de eventos de progresso.

Sem alterar a semântica de policy/resolução efetiva.

## 2) Cancelamento vs rollback

Cancelamento **não reverte** policies já `CREATED/UPDATED`.

Ele interrompe o processamento restante:

- `PENDING` → `CANCELLED` imediatamente (itens `PENDING` também viram `CANCELLED`);
- `RUNNING` → `CANCEL_REQUESTED` (worker para de pegar itens novos e cancela os itens `PENDING`).

## 3) Backoff por item

Quando um item falha e ainda não atingiu `maxAttempts`:

- volta para `PENDING`;
- recebe `nextRetryAt` calculado (exponencial com teto);
- worker ignora itens com `nextRetryAt` no futuro.

Ao exceder `maxAttempts`:

- item vira `FAILED` e `terminalFailure=true`.

## 4) Stale lock recovery

- Rollout `RUNNING/CANCEL_REQUESTED` com `lockedAt` antigo tem lock liberado.
- Item `RUNNING` com `lastLockedAt` antigo é recuperado para `PENDING` (com backoff) ou `FAILED` (terminal).

## 5) Rate-limit de progresso

Eventos `PAYMENT_POLICY_ROLLOUT_PROGRESS_UPDATED` são emitidos apenas quando:

- delta percentual mínimo foi atingido; **ou**
- intervalo mínimo desde o último evento passou.

Eventos finais (`COMPLETED`, `PARTIAL_FAILED`, `FAILED`, `CANCELLED`) continuam obrigatórios.

## 6) Re-run seguro

- bloqueado para `PENDING/RUNNING/CANCEL_REQUESTED`;
- preserva `manualOverride` por padrão;
- não reprocessa `CREATED/UPDATED`;
- limpa `nextRetryAt` e `terminalFailure` nos itens resetados;
- falha se não houver itens resetáveis.

## 7) Limitações

- Sem rollback de policies já aplicadas
- Sem cancelamento interruptivo de item já em execução
- Sem fila externa / SSE / UI

