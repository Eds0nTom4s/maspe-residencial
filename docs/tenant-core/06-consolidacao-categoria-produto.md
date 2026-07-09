# Prompt 6 — Consolidação de CategoriaProduto (FK) como Fonte Principal

Data: 2026-05-15

## Estado pós-Prompt 5 (problemas remanescentes)

- `Produto` já é tenant-owned (`produtos.tenant_id NOT NULL`, `UNIQUE(tenant_id, codigo)`).
- `CategoriaProduto` já existe como entidade (`categoria_produtos`).
- Dívida técnica deliberada:
  - `produtos.categoria_produto_id` ainda podia estar `NULL`.
  - fluxo tenant-aware ainda podia depender do enum `CategoriaProdutoLegacy`.
  - roteamento de produção (cozinha) ainda era baseado no enum legado.

## Objetivo do Prompt 6

1. Garantir `produtos.categoria_produto_id` preenchido para todos os registros.
2. Tornar `Produto.categoriaProduto` a referência operacional principal.
3. Reduzir o enum `CategoriaProdutoLegacy` para compatibilidade (não como fonte).
4. Ajustar DTOs e endpoints tenant-aware para aceitar/expor `categoriaProdutoId`.
5. Manter compatibilidade mínima com endpoints legados.

## Migração Flyway (V5)

Migration: `src/main/resources/db/migration/V5__consolidar_categoria_produto_fk.sql`

O que faz:

- Garante que todo tenant que tem produtos possui categorias default:
  - sempre cria `"Geral"` (`slug = geral`) e também os slugs padrão que mapeiam o enum legado.
- Backfill de `produtos.categoria_produto_id`:
  - tenta mapear pelo enum legado → slug equivalente
  - fallback final: `slug = geral`
- Aplica `ALTER TABLE produtos ALTER COLUMN categoria_produto_id SET NOT NULL`
- Garante índice `(tenant_id, categoria_produto_id)`

## DTOs

`ProdutoRequest`:

- adiciona `categoriaProdutoId` (preferido em endpoints tenant-aware)
- mantém `categoria` (`CategoriaProdutoLegacy`) como compatibilidade

`ProdutoResponse`:

- expõe:
  - `categoriaProdutoId`
  - `categoriaProdutoNome`
  - `categoriaProdutoSlug`
- mantém `categoria` (legado) como campo de compatibilidade

## Services / Controllers

### ProdutoService

- Métodos tenant-aware passam a operar com `CategoriaProduto` como fonte principal:
  - se `categoriaProdutoId` enviado → resolve por `id + tenantId`
  - se omitido → usa/cria default `"geral"`
- `CategoriaProdutoLegacy` vira apenas compatibilidade de persistência:
  - se não enviado, é derivado do slug quando possível, senão `OUTROS`.

### Endpoints tenant-aware

`/api/tenant/produtos`:

- aceita `categoriaProdutoId` no payload
- `GET` suporta filtro opcional por `categoriaProdutoId`

### Roteamento de cozinha (transição)

- `SubPedidoService` ganhou overload que recebe `Produto` e prefere:
  - `produto.categoriaProduto.slug` → mapeia para enum legado quando possível
  - fallback final para `produto.categoria` (legado)

Não foi implementado `UnidadeProducao/RotasProducao` nesta fase.

## Testes

- `src/test/java/com/restaurante/catalog/CatalogCategoryConsolidationIT.java`
  - valida que FK é obrigatória (não permite salvar produto sem `categoriaProduto`)
  - valida existência/criação de categoria default `"geral"`

- `src/test/java/com/restaurante/catalog/CatalogTenantIsolationIT.java`
  - ajustado para setar `categoriaProduto` nos produtos do teste

## Próximos passos

- Prompt 7 (recomendação): Catálogo público por QR token **tenant-safe** (sem catálogo global).
- Fase futura: remover de vez a dependência do enum legado (`produtos.categoria`) após refatorar os endpoints legados.
- Fase futura: `UnidadeProducao` e `RotasProducao` para roteamento real por categoria/produto.

