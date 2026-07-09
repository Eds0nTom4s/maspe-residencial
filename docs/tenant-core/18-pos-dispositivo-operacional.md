# Prompt 18 — Dispositivo Operacional (POS/KDS) tenant-aware

## Objetivo
Introduzir uma identidade própria para **dispositivos operacionais** (ex.: POS Android), separada de usuários humanos, com:
- registro pelo **TENANT_OWNER/TENANT_ADMIN** (gera `activationCode`)
- ativação pelo dispositivo (troca `activationCode` por `deviceToken`)
- armazenamento **somente em hash** (nunca plaintext)
- vínculo com `Tenant` + `Instituicao` + `UnidadeAtendimento`
- heartbeat e configuração inicial
- enforcement de `maxDispositivos` via `TenantLimitService`

## O que esta fase não implementa
- sincronização offline (fila, conflitos, bidirecional)
- POS criando pedido / iniciando pagamento
- impressão, WebSocket, push, KDS visual completo
- rotação/refresh token de device, blacklist distribuída

## Modelo
Entidade `DispositivoOperacional` (tabela `dispositivos_operacionais`) com:
- `tenant_id` (FK, NOT NULL)
- `instituicao_id` (FK, NOT NULL)
- `unidade_atendimento_id` (FK, NULL)
- `codigo` único por tenant: `unique(tenant_id, codigo)`
- `status`: `PENDENTE_ATIVACAO | ATIVO | SUSPENSO | REVOGADO | EXPIRADO`
- `activation_code_hash` + `activation_code_expires_at`
- `device_token_hash` + `device_token_issued_at` + `device_token_revoked_at`
- `ultimo_heartbeat_em` + metadados (`app_version`, `platform`, `user_agent`, `ultimo_ip`, etc.)

## Segurança de tokens
### Activation Code
- Curto (8 chars) para digitação manual.
- Expira por property: `consuma.device.activation-code-expiration-minutes` (default 30).
- Persistido apenas como **HMAC-SHA256 hex** (`activation_code_hash`).

### Device Token
- Opaque aleatório (`Base64URL` de 32 bytes).
- Retornado **uma única vez** no `POST /api/device/activate`.
- Persistido apenas como **HMAC-SHA256 hex** (`device_token_hash`).
- Revogação/suspensão remove o hash (invalidando uso futuro).

### Hashing
- HMAC-SHA256 com secret interno via property:
  - `consuma.device.token-hash-secret` (em produção via env `DEVICE_TOKEN_HASH_SECRET`)

## Enforcements de limite
`TenantLimitService.assertCanCreateDispositivo(tenantId, quantidadeNova)`:
- conta dispositivos com `status != REVOGADO`
- `SUSPENSO` ainda consome limite
- `REVOGADO` não consome limite

## Endpoints
### Tenant Admin (JWT tenant-scoped)
Base: `/api/tenant/dispositivos`  
Roles: `TENANT_OWNER`, `TENANT_ADMIN`

- `POST /api/tenant/dispositivos` → registra e retorna `activationCode` (apenas nessa resposta)
- `GET /api/tenant/dispositivos` → lista (pageable)
- `GET /api/tenant/dispositivos/{id}` → detalhe
- `POST /api/tenant/dispositivos/{id}/suspender` → suspende e invalida token
- `POST /api/tenant/dispositivos/{id}/revogar` → revoga e invalida token/código
- `POST /api/tenant/dispositivos/{id}/activation-code` → reemite `activationCode`

### Device (sem JWT de usuário)
Base: `/api/device` (permitAll no SecurityConfig)

- `POST /api/device/activate` → `activationCode` → emite `deviceToken`
- `POST /api/device/heartbeat` → `Authorization: Device <token>`
- `GET /api/device/config` → `Authorization: Device <token>`

Observações:
- endpoints device **não aceitam** `Bearer <jwt>` como autenticação de device.
- endpoints tenant **não aceitam** `Authorization: Device ...` como substituto de JWT.

## Capabilities
Enum `DeviceCapability` (informativo nesta fase), derivado do `DispositivoTipo`:
- POS: `HEARTBEAT`, `SYNC_CATALOG`, `VIEW_ORDERS`, `VIEW_PAYMENTS`
- KDS/COZINHA/BAR: `HEARTBEAT`, `VIEW_PRODUCTION`, `UPDATE_PRODUCTION_STATUS`
- CHECKOUT: `HEARTBEAT`, `VIEW_ORDERS`, `VIEW_PAYMENTS`
- QUIOSQUE: `HEARTBEAT`, `SYNC_CATALOG`, `VIEW_ORDERS`

## Próximos passos sugeridos
- DeviceAuthenticationFilter (Spring Security) para `/api/device/**` com principal de device.
- Rotação de `deviceToken` + tokenVersion por dispositivo.
- Sync inicial (catálogo/mesas) + fila offline unidirecional.
- Auditoria por dispositivo em pedidos/pagamentos/produção.

