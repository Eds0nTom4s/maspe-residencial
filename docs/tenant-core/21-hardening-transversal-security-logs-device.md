# Prompt 21 — Hardening transversal: JWT stale, OperationalEventLog e DevicePrincipal

## Objetivo
Mitigar três riscos antes de avançar para features de maior superfície (KDS-ready, sync POS, offline, WebSocket):

1. **JWT tenant-scoped stale** após mudança de roles/membership.
2. **Crescimento ilimitado** do `OperationalEventLog` sem proteção básica contra consultas pesadas.
3. **DevicePrincipal** não integrado ao fluxo operacional (auditoria e ações futuras de device).

---

## 1) Mitigação de JWT tenant-scoped stale

### Problema
Tokens `tokenType=TENANT` carregam `tenantRoles` e podem continuar válidos até expirar mesmo após:
- alteração de roles do usuário no tenant
- suspensão/remoção do membership

### Solução
Criada a tabela `tenant_user_access_versions` para controlar a **versão de acesso** por `(tenant_id, user_id)`.

- Ao emitir token TENANT via `POST /api/auth/tenant/select`, o token passa a carregar:
  - `tenantAccessVersion`
  - `tenantPermissionsUpdatedAt`
- Ao resolver `TenantContext` a partir do token, o `TenantResolver` valida se:
  - `tenantAccessVersion` do token **==** versão atual do banco
  - se não bater, retorna **401** com `code=TENANT_TOKEN_STALE`

### Quando a versão incrementa
Em ações de gestão de usuários do tenant (Prompt 19):
- criação/vínculo
- alteração de roles
- suspensão
- reativação
- remoção lógica

### Property
- `consuma.security.tenant-token.require-access-version=true`

---

## 2) Hardening do OperationalEventLog

### Problema
`operational_event_logs` é append-only e pode crescer indefinidamente. Sem guarda, endpoints podem virar ponto de DoS interno (consultas largas).

### Solução mínima
No endpoint `GET /api/tenant/operacional/eventos`:
- aplica **lookback padrão** se não houver filtros de data (default: 30 dias)
- limita **page size máximo** (default: 100)

Endpoints adicionais:
- `GET /api/tenant/operacional/eventos/resumo`: contagem por período + agregações básicas por `eventType` e `origem`
- `POST /api/tenant/operacional/eventos/retencao/dry-run`: conta eventos antigos (sem deletar)
- `POST /api/tenant/operacional/eventos/retencao/execute`: apaga eventos antigos **somente se habilitado por property**

### Properties
- `consuma.operational-events.default-lookback-days=30`
- `consuma.operational-events.max-page-size=100`
- `consuma.operational-events.retention-days=180`
- `consuma.operational-events.cleanup-enabled=false`

---

## 3) DevicePrincipal nos endpoints operacionais

### Problema
Dispositivos já existem (`DispositivoOperacional`), mas o backend ainda não tratava device como actor “de primeira classe” nos endpoints operacionais.

### Solução
Criado `DeviceAuthenticationFilter` (header `Authorization: Device <token>`) com allowlist de paths:
- `/api/device/**` e `/device/**`
- `/api/tenant/producao/**` e `/tenant/producao/**`

Isso evita que deviceToken autentique endpoints administrativos gerais do tenant.

Auditoria:
- Adicionada a tabela `device_event_logs` para eventos de segurança/telemetria do device (AUTH_SUCCESS, TOKEN_ROTATED, etc.).
- `OperationalEventLogService` passa a suportar actor `DEVICE` quando `DevicePrincipal` estiver no `SecurityContext`.

Rotação de token:
- `POST /api/device/token/rotate` / `/device/token/rotate`

---

## O que fica para o futuro
- refresh token completo e revogação distribuída
- cache seguro de membership + version check (para reduzir roundtrips ao banco)
- particionamento/arquivamento de `operational_event_logs`
- device sync offline, POS criando pedido, WebSocket/SSE

