# Prompt 28 — Hardening de secrets por profile + erros padronizados (device) + métricas de sync/ETag

Data: 2026-05-18

## Objetivo
Antes de permitir escrita por POS/KDS, reforçar segurança operacional e observabilidade mínima:

1) Bloquear secrets default/fracos no profile `prod` (fail-fast no startup).
2) Padronizar erros de `/device/*` (activate/heartbeat/config/token rotate) com um contrato próprio para apps.
3) Introduzir métricas de sync/auth/heartbeat de forma incremental (Micrometer se existir; caso contrário NoOp).

## 1) Hardening de secrets por profile
### Secrets validados
- `consuma.device.token-hash-secret` (HMAC para hash de activationCode/deviceToken)
- `consuma.sync.cursor.hmac-secret` (HMAC de cursor)

### Regras
- Em `prod`:
  - secret obrigatório (não vazio)
  - mínimo recomendado: 32 caracteres
  - bloqueia valores default de desenvolvimento (ex: `dev-*-change-me`)
- Em `dev/test`:
  - permite, mas loga warning

Implementação: `SensitiveSecretsValidator` (fail-fast via `IllegalStateException` no startup).

## 2) DeviceErrorResponse para /device/*
### Motivação
O app POS/KDS precisa interpretar falhas de autenticação/estado do device de forma previsível (sem depender de mensagens/stacktrace).

DTO: `DeviceErrorResponse`
- `code`, `message`
- `recoverable`, `action`
- `serverTime`
- `details` sanitizado (opcional)

Handler: `DeviceApiExceptionHandler`
- aplica-se apenas a `/device/*` (exceto `/device/sync/*`)
- mapeia:
  - 401 `DEVICE_UNAUTHORIZED`
  - 403 `DEVICE_FORBIDDEN`
  - 400 `DEVICE_REQUEST_INVALID`

## 3) Métricas mínimas (Micrometer opcional)
Interface: `DeviceSyncMetricsService`
- `recordSyncRequest(domain, result)`
- `recordEtagHit(domain)`
- `recordEtagMiss(domain)`
- `recordFullSyncRequired(domain, reason)`
- `recordCursorError(domain, code)`
- `recordDeviceAuth(result)`
- `recordHeartbeat(result)`
- `timeSync(domain, Supplier<T>)`

Implementações:
- `MicrometerDeviceSyncMetricsService` (ativada quando `MeterRegistry` existe)
- `NoOpDeviceSyncMetricsService` (fallback quando Micrometer não está presente)

### Cardinalidade
Tags limitadas a valores pequenos:
- `domain`: BOOTSTRAP, CATALOGO, MESAS, QRCODES, PRODUCAO, PRODUCAO_FILA, PRODUCAO_FILA_DIFF
- `result`: 200/304/OK/UNAUTHORIZED/FORBIDDEN etc.
- `reason`/`code`: enums controlados

## Logs seguros
Regras:
- nunca logar `deviceToken`, `activationCode`, cursor completo ou secrets.
- logar apenas: domínio, código de erro, reason, deviceId quando disponível.

## Próximos passos sugeridos
- Bloquear secret default de forma explícita por ambiente (ex: profile `prod` + property “strictSecrets=true”).
- Exportar métricas para Prometheus/Grafana quando Actuator/Micrometer estiverem ativos na stack.
- Padronizar também erros de `/device/sync/*` (já existe `SyncErrorResponse`) e `/device/*` em conjunto no app.

