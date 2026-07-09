# Prompt 5 — Catálogo Tenant-Aware (Produto + CategoriaProduto)

Data: 2026-05-15

## Objetivo

Remover o bloqueador crítico de multi-tenant: **catálogo global**.

Nesta fase o catálogo passa a ser **tenant-owned** (nível `Tenant`), sem ainda introduzir catálogo específico por `Instituicao`.

## Decisão: enum legado vs entidade

- O enum antigo `CategoriaProduto` foi mantido **apenas para compatibilidade**, renomeado para `CategoriaProdutoLegacy`.
- A nova categoria tenant-owned passa a ser a entidade `CategoriaProduto` (tabela `categoria_produtos`).
- A migração completa enum → FK foi feita **de forma incremental**:
  - `produtos.categoria` (enum) permanece obrigatória por enquanto.
  - `produtos.categoria_produto_id` existe e é backfilled quando possível, mas permanece **nullable** nesta fase.

## Alterações de banco (Flyway)

- Migration: `src/main/resources/db/migration/V4__catalogo_tenant_aware.sql`
  - Cria `categoria_produtos` (tenant-owned) com unique `(tenant_id, slug)`.
  - Adiciona `tenant_id NOT NULL` em `produtos` + FK para `tenants`.
  - Remove unique global de `produtos.codigo` e cria **unique composto** `(tenant_id, codigo)`.
  - Seeds de categorias default para `Tenant LEGACY` e backfill best-effort de `categoria_produto_id`.

## Regras resultantes

- `Produto` pertence obrigatoriamente a `Tenant` (`produtos.tenant_id NOT NULL`).
- Dois tenants podem ter `Produto.codigo` igual.
- Um mesmo tenant **não** pode duplicar `Produto.codigo`.
- `CategoriaProduto.slug` é único por tenant.

## Compatibilidade com legado (anti-leak)

Enquanto endpoints legados ainda existirem, leituras/escritas de `ProdutoService` passam a operar:

- se houver `TenantContext` → **tenant atual**
- caso contrário → **somente `Tenant LEGACY`**

Isso evita que endpoints antigos listem/alterem catálogo de outros tenants.

## Endpoints tenant-aware adicionados

- `GET /api/tenant/produtos`
- `POST /api/tenant/produtos`
- `GET /api/tenant/produtos/{id}`

- `GET /api/tenant/categorias-produto`
- `POST /api/tenant/categorias-produto`

Observação: estes endpoints exigem `TenantContext` (resolvido via Prompt 4) e membership via `TenantGuard`.

## Testes

- Integração (PostgreSQL/Testcontainers): `src/test/java/com/restaurante/catalog/CatalogTenantIsolationIT.java`
  - prova isolamento entre dois tenants
  - prova unique por tenant (produto/categoria)
  - prova que service tenant-aware não lê cross-tenant

## Próximos passos (fases futuras)

- Refatorar `PublicCardapioController` para catálogo público por QR token (tenant-safe), removendo dependência de legado.
- Completar migração `Produto.categoria` (enum) → `categoria_produto_id` obrigatório.
- Tornar repos/queries de catálogo estritamente tenant-scoped em todos os fluxos.
- Avaliar catálogo por `Instituicao` (opcional) para casos multi-instituição.

