# Tenant Core — Vincular `Instituicao` ao `Tenant` (Prompt 2)

Data: 2026-05-14

## Objetivo

Criar o primeiro elo real entre o domínio SaaS e o domínio operacional existente:

- `Tenant 1:N Instituicao`
- `Instituicao` passa a ter `tenant_id` obrigatório

Sem implementar ainda:
- `TenantContext` / `TenantResolver` / `TenantGuard`
- mudanças tenant-aware em `Produto`, `Pedido`, `Pagamento`, `SessaoConsumo`, QR, POS
- remoção de `getInstituicaoAtiva()` / `findFirstByAtivaTrue()`

## Mudanças introduzidas

### Domínio (JPA)
- `Instituicao` agora possui:
  - `@ManyToOne(optional=false)` para `Tenant`
  - coluna `tenant_id` NOT NULL

### Migração Flyway
- `V3__link_instituicao_to_tenant.sql`
  - adiciona `tenant_id` em `instituicoes`
  - cria um `Tenant` LEGACY (tenant_code=`LEGACY`) se não existir
  - backfill: vincula instituições existentes ao tenant LEGACY
  - impõe NOT NULL + FK

### Compatibilidade legada
- `DataSeeder` (profile `dev`) passa a criar um tenant LEGACY e associar a Instituição seed.

## Testes

- Em PostgreSQL real (Testcontainers, profile `it-postgres`):
  - valida que um tenant pode ter múltiplas instituições
  - valida que `Instituicao` não pode existir sem `Tenant`

## Próximo passo

Prompt 3:
- introduzir validação de limites por plano/override (sem ainda implementar TenantContext)

