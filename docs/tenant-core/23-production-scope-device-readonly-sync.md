# Prompt 23 — Seleção explícita de unidade de produção + Sync read-only (POS/KDS)

## Objetivo
Concluir a preparação para operação distribuída (KDS/POS) com:
- Remoção da ambiguidade de “minha unidade” (cozinha/bar/pastelaria) via **seleção persistida** para usuário `TENANT_KITCHEN` (e opcionalmente OWNER/ADMIN/OPERATOR).
- Preferência de unidade de produção por **dispositivo** (KDS/POS) via `unidade_producao_id`.
- Endpoints HTTP **read-only** de sync para dispositivos, com `updatedSince` e `syncGeneratedAt`.

Fora do escopo:
- Offline bidirecional / fila offline / resolução de conflitos
- POS criando pedidos / iniciando pagamentos
- WebSocket/SSE
- Impressão térmica / KDS visual

---

## Migração (Flyway)
`V19__production_scope_selection_and_readonly_sync.sql`

Inclui:
- `tenant_user_production_scopes` (seleção por usuário+tenant)
- `dispositivos_operacionais.unidade_producao_id` (preferência por device)

---

## Seleção por usuário (TENANT_KITCHEN)
### Endpoints
Base: `/tenant/producao`

- `GET /tenant/producao/minha-unidade`
  - Resolve na ordem:
    1) seleção persistida (`tenant_user_production_scopes`)
    2) única unidade ativa no tenant
    3) múltiplas unidades → `EXPLICIT_REQUIRED` com `opcoes`

- `POST /tenant/producao/minha-unidade`
  - Body: `{ "unidadeProducaoId": 123 }`
  - Cria/atualiza seleção persistida.

- `DELETE /tenant/producao/minha-unidade`
  - Desativa seleção persistida e volta ao modo automático.

### Regras
- Tenant vem do `TenantContext` (nunca via request).
- UnidadeProducao deve pertencer ao tenant (anti-cross-tenant).
- RBAC: `TENANT_OWNER|TENANT_ADMIN|TENANT_OPERATOR|TENANT_KITCHEN`.

---

## Preferência por device
### Alteração de modelo
`DispositivoOperacional` recebeu `unidadeProducao` opcional.

### Endpoints tenant-admin
Base: `/tenant/dispositivos`

- `POST /tenant/dispositivos` aceita `unidadeProducaoId` opcional.
- `POST /tenant/dispositivos/{id}/unidade-producao`
  - Body: `{ "unidadeProducaoId": 123 }`
  - RBAC: `TENANT_OWNER|TENANT_ADMIN`.

### Resolução no runtime
`ProducaoScopeResolver` para device resolve na ordem:
1) `DevicePrincipal.unidadeProducaoId` (preferência explícita)
2) unidade vinculada a `unidadeAtendimentoId`
3) fallback seguro por instituição/tenant (single active)
4) ambiguidade → 409 `DEVICE_PRODUCTION_UNIT_AMBIGUOUS`

---

## Sync read-only para device
Base: `/device/sync`

Todos exigem:
- `Authorization: Device <token>`
- `DevicePrincipal` no `SecurityContext`

### Endpoints
- `GET /device/sync/bootstrap`
- `GET /device/sync/catalogo?updatedSince=&includeInactive=`
  - Capability: `SYNC_CATALOG`
- `GET /device/sync/mesas?updatedSince=&unidadeAtendimentoId=`
  - Capability: `VIEW_ORDERS` **ou** `SYNC_CATALOG`
- `GET /device/sync/qrcodes?updatedSince=`
  - Capability: `VIEW_ORDERS` **ou** `SYNC_CATALOG`
- `GET /device/sync/producao?updatedSince=`
  - Capability: `VIEW_PRODUCTION`
- `GET /device/sync/producao/fila?...`
  - Capability: `VIEW_PRODUCTION`
  - Reusa o motor do Prompt 22 (`ProducaoKdsService`) para fila paginada e lookback.

### Estratégia incremental
- `updatedSince` (ISO_DATE_TIME) é suportado onde há `updatedAt` (auditoria JPA) para retornar apenas mudanças.
- `syncGeneratedAt` é retornado em todas as respostas para o device usar como checkpoint.

### Segurança
- Tenant é sempre o do `DevicePrincipal`.
- Sync não retorna financeiro, usuários, secrets nem hashes.
- Tokens device não dão acesso a endpoints tenant-admin.

---

## Próximos passos sugeridos
1) Persistir “seleção de unidade” para `TENANT_KITCHEN` via UI futura (sem mudar backend).
2) Adicionar ETag/If-None-Match nos endpoints de sync mais “pesados” (catálogo/produção).
3) Sync incremental mais robusto (paginação + cursor) para catálogos grandes.
4) Read-only sync de fila + “diff” por OperationalEventLog (para SSE/WebSocket futuro).

