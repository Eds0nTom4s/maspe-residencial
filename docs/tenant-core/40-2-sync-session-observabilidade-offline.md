# Prompt 40.2 — Sync Session Header + Observabilidade/Troubleshooting Offline

## Objetivo

Adicionar uma camada de observabilidade operacional para o `offline-sync` do POS, preservando:

- idempotência forte por `tenant + device + clientRequestId`;
- `localRef/dependsOn` do Prompt 40.1;
- bloqueios offline (AppyPay/OTP/fecho/snapshot continuam proibidos).

O foco é suporte em produção: “qual batch chegou, de qual device, quantos comandos aplicaram/falharam e por quê”.

## Conceitos

### Comando offline (já existia)

Persistido em `device_offline_commands` por item.

### Sync session (novo)

Persistido em `device_offline_sync_sessions` por batch.

A sync session guarda:

- contexto do batch (device, unidade, appVersion, período offline declarado);
- status agregado;
- contadores (applied/duplicate/rejected/conflict/failed);
- duração;
- `serverSyncId` (gerado no backend) para troubleshooting.

## Identificadores

### `serverSyncId`

Gerado no backend para identificar uma sessão de sync de forma confiável e auditável.

### `syncSessionId`

Opcional (enviado pelo device). Para MVP:

- se o device enviar `syncSessionId` e já existir sessão para o mesmo `tenant+device+syncSessionId`, a sessão é reaproveitada;
- comandos permanecem idempotentes por `clientRequestId` independentemente de `syncSessionId`.

## Status de sessão

Enum `DeviceOfflineSyncSessionStatus`:

- `RECEIVED`: criado no início;
- `PROCESSING`: batch em processamento;
- `COMPLETED`: sem `failed` e sem `conflict`, sem rejeições;
- `COMPLETED_WITH_WARNINGS`: sem `failed`/`conflict`, mas com rejeições;
- `PARTIAL_FAILED`: houve `failed`/`conflict` mas algo aplicou/duplicou;
- `FAILED`: tudo falhou/conflitou;
- `REJECTED`: batch rejeitado antes de processar (payload/limites/dependência inválida etc).

## Persistência

- `device_offline_sync_sessions`: cabeçalho/contadores do batch.
- `device_offline_commands` recebe:
  - `sync_session_db_id` (FK para session);
  - `server_sync_id` (cópia do `serverSyncId` para facilitar query).

## Sanitização

Os endpoints de observabilidade retornam apenas resumo seguro por comando (sem `payload_json`).

`summary_json` e `error_summary_json` na sessão guardam apenas agregações (contadores e distribuição de códigos).

## Endpoints

### Device

- `POST /device/offline-sync/batch`
  - agora retorna também: `serverSyncId`, `syncSessionId`, `syncSessionStatus`, timestamps e `durationMs`.
- `GET /device/offline-sync/sessions`
  - lista as últimas sessões do próprio device.
- `GET /device/offline-sync/sessions/{serverSyncId}`
  - retorna detalhe da sessão, apenas se pertence ao device autenticado.

### Tenant/Admin

- `GET /tenant/offline-sync/sessions`
  - lista paginada com filtros: `unidadeId`, `deviceId`, `status`, `appVersion`, `dateFrom/dateTo`, flags de falha/conflito/rejeição.
- `GET /tenant/offline-sync/sessions/{serverSyncId}`
  - detalhe da sessão (inclui `summary` e `errorSummary`).
- `GET /tenant/offline-sync/sessions/{serverSyncId}/commands`
  - lista paginada de comandos da sessão (resumo seguro).
- `GET /tenant/offline-sync/metrics`
  - métricas agregadas simples (top conflict/error codes, breakdown por appVersion, ranking de devices com mais `FAILED`).

RBAC:

- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`: acesso.
- `OPERATOR/KITCHEN`: bloqueados (via `TenantGuard`).

## Auditoria

Eventos operacionais adicionados/ativados:

- `DEVICE_OFFLINE_SYNC_SESSION_CREATED`
- `DEVICE_OFFLINE_SYNC_SESSION_REJECTED`
- `DEVICE_OFFLINE_SYNC_SESSION_COMPLETED`
- `DEVICE_OFFLINE_SYNC_SESSION_PARTIAL_FAILED`
- `DEVICE_OFFLINE_SYNC_TROUBLESHOOTING_VIEWED` (controlado por property)

Sem payload sensível, sem deviceToken, sem OTP.

## Limitações

- não implementa dashboard UI;
- não implementa export/download de diagnóstico;
- não implementa reprocessamento manual por session;
- não implementa fila/worker async para sync offline (batch continua síncrono).

