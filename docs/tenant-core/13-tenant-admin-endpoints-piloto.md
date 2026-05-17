# Prompt 13 — Tenant Admin APIs mínimas (piloto)

## Objetivo
Disponibilizar uma superfície mínima de APIs **tenant-admin** (sem frontend) para permitir operação do piloto após provisionamento:
- contexto do tenant (`/tenant/me`)
- estrutura do tenant (instituições/unidades/mesas)
- QR operacional (listar/gerar/revogar)
- pedidos (listar/detalhar)

Todos os endpoints são **tenant-aware** e exigem **TenantContext** + **membership ativo** no tenant.

## Endpoints (base `/api` via `server.servlet.context-path=/api`)

### Contexto do tenant
- `GET /api/tenant/me`

Retorna:
- tenant + plano ativo + limites efetivos
- usuário atual (quando resolvido)
- resumo de instituições

### Estrutura
- `GET /api/tenant/instituicoes`
- `GET /api/tenant/instituicoes/{id}`
- `GET /api/tenant/unidades?instituicaoId&ativa`
- `GET /api/tenant/unidades/{id}`
- `GET /api/tenant/mesas?instituicaoId&unidadeAtendimentoId&ativa`
- `GET /api/tenant/mesas/{id}`

### QR operacional
- `GET /api/tenant/qrcodes`
- `GET /api/tenant/qrcodes/{id}`
- `POST /api/tenant/qrcodes/{id}/revogar`
- `POST /api/tenant/mesas/{mesaId}/qrcode` (gera QR tipo `MESA`)
  - regra atual: se já existe QR ativo para a mesa, retorna o existente (evita multiplicação)

### Pedidos
- `GET /api/tenant/pedidos` (paginado)
  - filtros: `statusOperacional`, `statusFinanceiro`, `instituicaoId`, `unidadeAtendimentoId`, `mesaId`, `de`, `ate`
- `GET /api/tenant/pedidos/{id}`

## Segurança e anti-cross-tenant
- Tenant é sempre derivado do `TenantContext` (não aceita `tenantId` via query).
- Todo acesso a recurso por `id` valida pertença ao tenant via query tenant-scoped.
- Cross-tenant retorna **404** com mensagem genérica (“Recurso não encontrado.”).
- Tokens de QR são retornados em endpoints tenant-admin (para impressão) e continuam protegidos por membership.

## Observações
- Financeiro (Prompt 9.3) permanece em `/api/tenant/financeiro/**` e não foi duplicado aqui.
- Produtos/categorias já existem em `/api/tenant/produtos` e `/api/tenant/categorias-produto`.

