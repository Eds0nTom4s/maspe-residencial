# Prompt 40.4 — Replay Assíncrono com `operationId` + Progress Tracking + Rate-limit por Tenant

## Objetivo

Evoluir o replay controlado do Prompt 40.3 (síncrono) para um **modelo assíncrono rastreável**, adequado a tenants grandes:

- `operationId` gerado no backend;
- persistência de operação (header) + itens por comando;
- worker/job para processar itens em batch;
- progress tracking e status final;
- rate-limit por tenant (evitar sobrecarga);
- locks para impedir replay concorrente do mesmo comando;
- re-run seguro de operação parcialmente falhada/bloqueada;
- auditoria sanitizada (sem payload sensível).

## Replay síncrono vs replay assíncrono

- **Síncrono (40.3)**: admin chama replay e recebe resultado final no mesmo request.
- **Assíncrono (40.4)**: admin **submete** e recebe `operationId`; worker processa; admin consulta progresso e itens.

O Prompt 40.4 **não muda** as regras de elegibilidade do Prompt 40.3 — apenas o modo de execução.

## Regras que permanecem inalteradas

- `APPLIED` **nunca** é reprocessado.
- `DUPLICATE` **nunca** é reprocessado.
- `IDEMPOTENCY_CONFLICT` **nunca** é reprocessado.
- `clientRequestId` e `payloadHash` são imutáveis.
- Replay usa o `payload_json` persistido (sem edição).
- Replay continua revalidando capability/policy/turno via handlers existentes.
- AppyPay offline continua proibido.
- OTP offline continua proibido.
- Fecho de turno / snapshot / evidence bundle continuam proibidos offline.
- Replay exige `reason` obrigatório.
- Replay é **tenant/admin only**.

## Modelo de dados

### `device_offline_replay_operations`

Header da operação assíncrona:

- `operation_id` (public id) + `server_sync_id`;
- `status` (PENDING/RUNNING/COMPLETED/…);
- counters (pending/running/succeeded/noop/blocked/failed);
- `progress_percent`;
- locks (`locked_at`/`locked_by`) e timestamps;
- filtros usados no submit (statuses/types/ids em JSON).

### `device_offline_replay_operation_items`

Itens por comando:

- `device_offline_command_id` + `client_request_id`;
- `previous_status`;
- `item_status` (PENDING/RUNNING/SUCCEEDED/…);
- `eligibility_status`/`eligibility_reason`;
- `attempts` + `next_retry_at` (backoff);
- `replay_attempt_id` (link para o attempt do 40.3, quando existir).

### `device_offline_commands` (campos adicionais)

- `replay_in_progress` + `current_replay_operation_id` para impedir concorrência do mesmo comando entre operações.

## Rate-limit por tenant (submit)

Antes de criar uma operação:

- limita quantidade de operações `PENDING/RUNNING` por tenant;
- limita operações submetidas na última hora;
- bloqueia submit concorrente para o mesmo `serverSyncId`;
- bloqueia operação vazia (sem comandos elegíveis).

## Worker / processamento

- O worker busca a próxima operação `PENDING/RUNNING` elegível e tenta lock via `locked_at`.
- Itens são “claimed” em batch (PENDING + `nextRetryAt <= now`) com `FOR UPDATE SKIP LOCKED`.
- Cada item roda com backoff por tentativa (`nextRetryAt`) e limite `maxAttemptsPerItem`.
- Itens `BLOCKED` não retentam automaticamente (podem ser reprocessados via re-run manual).
- A operação recalcula counters e `progress_percent` periodicamente.

## Progress + auditoria com rate-limit

Eventos de progresso não são emitidos a cada item:

- somente quando há delta percentual mínimo e intervalo mínimo (config);
- eventos finais sempre são emitidos (COMPLETED / PARTIAL_FAILED / FAILED).

## Endpoints (tenant/admin)

Base: `/tenant/offline-sync`

- `POST /sessions/{serverSyncId}/replay/submit`
- `GET /replay-operations/{operationId}`
- `GET /replay-operations/{operationId}/items`
- `POST /replay-operations/{operationId}/rerun`

RBAC:

- `TENANT_OWNER`/`TENANT_ADMIN`: submit + rerun + status/items
- `TENANT_FINANCE`: status/items (sem submit/rerun)

## Limitações (intencionais)

- Não usa fila externa (Kafka/Rabbit/SQS).
- Não tem WebSocket/SSE.
- Não implementa cancelamento avançado de operação.
- Não permite edição de payload para “corrigir” comando.
- Não faz rollback financeiro.

## Próximo passo recomendado

- Cancelamento controlado de replay operations (PENDING cancelável + cancelRequested para RUNNING), alinhado ao hardening do rollout (38.5).
- UI de troubleshooting no painel (fora de escopo).

