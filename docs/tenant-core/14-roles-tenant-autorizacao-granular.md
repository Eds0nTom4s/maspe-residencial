# Prompt 14 — RBAC Tenant (autorização granular)

## Objetivo
Endurecer permissões **dentro do tenant** (RBAC) para reduzir risco de acesso indevido entre usuários do mesmo cliente.

Até aqui o backend já fazia:
- isolamento por tenant (tenant-scoped / 404 cross-tenant)
- membership ativo (TenantUser ATIVO)

Este prompt adiciona:
- exigência de **TenantUserRole** por endpoint/ação
- `403` quando role insuficiente (sem vazar detalhes)

## Fontes de verdade
- Membership e roles vivem em `tenant_users` (TenantUserRole + TenantUserEstado).
- `TenantContext` é request-scoped e agora inclui roles do tenant selecionado (quando possível).

## Como roles são resolvidas
- `TenantResolver` agrega:
  - authorities do Spring (`ROLE_*`)
  - roles tenant do membership ativo (`TENANT_*`) para o tenant resolvido
- `TenantGuard` valida roles via:
  - fast path: `TenantContext.roles`
  - fallback: consulta `TenantUserRepository` (defesa em profundidade)

## Matriz aplicada (fase inicial)

### `/api/tenant/me`
Permitido:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_FINANCE`, `TENANT_CASHIER`, `TENANT_KITCHEN`

### Estrutura (`/api/tenant/instituicoes`, `/unidades`, `/mesas`)
Permitido:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`, `TENANT_FINANCE`
Bloqueado:
- `TENANT_KITCHEN`

### QR (`/api/tenant/qrcodes`)
Listar/detalhar:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`
Revogar/gerar:
- `TENANT_OWNER`, `TENANT_ADMIN`

### Pedidos (`/api/tenant/pedidos`)
Listar/detalhar:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`, `TENANT_FINANCE`
Bloqueado:
- `TENANT_KITCHEN` (até existirem endpoints próprios de cozinha/subpedido)

### Financeiro (`/api/tenant/financeiro/**`)
Permitido:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`

### Catálogo tenant-aware (já existente)
- `/api/tenant/produtos`
- `/api/tenant/categorias-produto`

GET:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`
POST (criar):
- `TENANT_OWNER`, `TENANT_ADMIN`

## Política 403 vs 404
- `404` continua sendo usado quando um **recurso** não pertence ao tenant (anti data leakage).
- `403` é usado quando o usuário pertence ao tenant, mas **não tem role suficiente**.

O handler de `AccessDeniedException` retorna:
- HTTP `403`
- `code = TENANT_ROLE_FORBIDDEN`

## Próximos passos (futuros)
- Integrar roles tenant no JWT (claims) para reduzir consultas a `tenant_users`.
- Endpoints específicos para cozinha (`/api/tenant/cozinha/...`) e RBAC dedicado.
- Gestão de usuários/roles pelo tenant admin.

