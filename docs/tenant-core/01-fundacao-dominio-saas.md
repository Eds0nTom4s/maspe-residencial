# Tenant Core — Fundação do Domínio SaaS (Prompt 1)

Data: 2026-05-14

## Objetivo

Introduzir o **domínio SaaS** em paralelo ao domínio operacional existente, sem alterar ainda:
- `Instituicao`, `Produto`, `Pedido`, `Pagamento`, `SessaoConsumo`, `QrCodeToken`, `UnidadeAtendimento`, `Cozinha`
- fluxos legados (single-tenant)
- `getInstituicaoAtiva()` / `findFirstByAtivaTrue()`

Nesta fase, criamos as entidades e persistência necessárias para a evolução multi-tenant **Tenant 1:N Instituicao**,
mas sem vincular `Instituicao` ao `Tenant` ainda.

## Entidades introduzidas

### `Tenant`
Representa a conta SaaS do cliente na plataforma CONSUMA.

Regras:
- `slug` único
- `tenantCode` único (curto, estável; futuro uso em referência de pagamentos/QR/auditoria)

Enums:
- `TenantEstado`: RASCUNHO, ATIVO, SUSPENSO, BLOQUEADO, CANCELADO
- `TenantTipo`: VENDEDOR_RUA, RESTAURANTE, BAR, FOOD_COURT, EVENTO, LOJA, CLUBE, INSTITUCIONAL

### `Plano`
Representa o plano comercial (limites e features).

Seed obrigatório:
- Plano `PILOTO` (preço 0, limites iniciais)

### `Subscricao`
Vínculo entre `Tenant` e `Plano`.

Regras:
- no máximo 1 subscrição `ATIVA` por tenant (índice parcial PostgreSQL)

Enum:
- `SubscricaoEstado`: ATIVA, EXPIRADA, SUSPENSA, CANCELADA

### `TenantUser`
Membership de `User` dentro de um `Tenant`.

Regras:
- unique(tenant_id, user_id, role)

Enums:
- `TenantUserRole`: TENANT_OWNER, TENANT_ADMIN, TENANT_OPERATOR, TENANT_FINANCE, TENANT_KITCHEN, TENANT_CASHIER
- `TenantUserEstado`: ATIVO, PENDENTE, SUSPENSO, REMOVIDO

### `TenantLimiteOverride`
Override manual de limites por tenant (sobrescreve limites do plano).

Regras:
- no máximo 1 override `ativo=true` por tenant (índice parcial PostgreSQL)

## Migrações Flyway

- `V1__baseline_schema.sql`: baseline do domínio atual (pré Tenant Core)
- `V2__create_tenant_core.sql`: cria tabelas do Tenant Core + seed do Plano `PILOTO`

Regra: toda mudança estrutural futura entra via migration (V3, V4, ...).

## Por que Tenant separado de Instituicao?

- `Tenant` representa a conta SaaS (plano, subscrição, limites, usuários, auditoria).
- `Instituicao` representa um estabelecimento/operação pertencente ao tenant.
- Isso permite evolução natural para **Tenant 1:N Instituicao** sem acoplar responsabilidades.

## Próximas fases

Prompt 2 (Fase 2): linkar `Instituicao` a `Tenant` com migration dedicada e compatibilidade com o modo legado.

