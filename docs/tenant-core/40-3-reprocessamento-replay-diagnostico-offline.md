# Prompt 40.3 — Reprocessamento Assistido / Replay Controlado por Sync Session + Export de Diagnóstico Sanitizado

## Objetivo

Adicionar capacidade operacional (tenant/admin) para:

- **avaliar elegibilidade** de replay de comandos dentro de uma `sync session`;
- **executar replay controlado** apenas para comandos elegíveis;
- **gerar export sanitizado** de diagnóstico para suporte (JSON), sem expor payload sensível.

O replay **não** substitui a idempotência por comando e **não** altera payload/clientRequestId.

## Replay vs reenvio do POS

- **Reenvio do POS**: o device reenfileira e reenvia o batch; o backend responde idempotente por comando.
- **Replay controlado**: o backend reexecuta **o mesmo comando persistido** (payloadJson + payloadHash), sob intervenção admin, para casos de falha/conflito corrigível (ex.: produto reativado, policy/capability corrigida).

## Regras de segurança e consistência

- Nunca reprocessar `APPLIED` ou `DUPLICATE`.
- Nunca reprocessar `IDEMPOTENCY_CONFLICT`.
- Nunca editar payload nem `clientRequestId`.
- Replay sempre revalida regras atuais (capabilities/policies/turno/estado de entidades) via handlers existentes.
- Replay é exclusivo de tenant/admin (device não executa replay).

## Modelo de dados

### `device_offline_command_replay_attempts`

Histórico de tentativas de replay por comando:

- status de elegibilidade;
- status do replay;
- reason (motivo) obrigatório informado pelo operador;
- timestamps;
- resumo de resultado/erro.

### `device_offline_commands` (campos adicionais)

- `replay_count`
- `last_replay_attempt_at`
- `last_replay_attempt_id`

## Elegibilidade (MVP)

Elegível apenas se status atual for: `FAILED`, `CONFLICT` ou `REJECTED`, e:

- não é `IDEMPOTENCY_CONFLICT`;
- não tem createdEntity já preenchida;
- dependências (`dependsOnClientRequestId`) já estão `APPLIED`/`DUPLICATE` no mesmo device (localRef entre batches é permitido);
- `REGISTER_LOCAL_ACTIVITY` é tratado como baixo valor (não replay por padrão).

`CONFIRM_MANUAL_PAYMENT` é mais rigoroso:

- se ordem já estiver `CONFIRMADA`, o replay é bloqueado.

## Endpoints

Base: `/tenant/offline-sync`

### Preview

`POST /sessions/{serverSyncId}/replay/preview`

- Retorna lista com elegibilidade e reason por comando.
- Não persiste replay attempt.

### Replay por session

`POST /sessions/{serverSyncId}/replay`

- Executa replay controlado (por statuses ou commandIds).
- `reason` obrigatório.
- Cria replay attempts e, em sucesso, atualiza o comando original para `APPLIED` com `result_json` atualizado.

### Replay por comando

`POST /commands/{commandId}/replay`

- Replay de um comando individual.

### Listar attempts

- `GET /sessions/{serverSyncId}/replay-attempts`
- `GET /commands/{commandId}/replay-attempts`

### Export diagnóstico sanitizado

`GET /sessions/{serverSyncId}/diagnostic-export`

