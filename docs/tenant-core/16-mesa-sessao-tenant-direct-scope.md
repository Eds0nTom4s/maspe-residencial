# Prompt 16 — `tenant_id` direto em Mesa e SessaoConsumo

## Objetivo
Fortalecer escopo multi-tenant e performance operacional removendo dependência de joins indiretos:

- `Mesa` deixa de depender de `Mesa → UnidadeAtendimento → Instituicao → Tenant`
- `SessaoConsumo` deixa de depender de `SessaoConsumo → Instituicao → Tenant`

Agora ambos passam a ter **escopo direto** por `tenant_id`, permitindo:
- queries rápidas tenant-scoped (POS, ocupação, relatórios operacionais)
- defesa em profundidade contra cross-tenant
- redução de custo de joins em paths críticos

## Migração (Flyway)
`V13__mesa_sessao_tenant_direct_scope.sql`

### Mesas
- adiciona `mesas.tenant_id`
- backfill por:
  1) `mesas.unidade_atendimento_id → unidades_atendimento.instituicao_id → instituicoes.tenant_id`
  2) fallback: `mesas.instituicao_id → instituicoes.tenant_id`
- valida que não sobra `tenant_id IS NULL`
- aplica `NOT NULL` + `FK (tenant_id → tenants.id)`
- cria índices tenant-scoped

### Sessões de Consumo
- adiciona `sessoes_consumo.tenant_id`
- backfill por:
  1) `sessoes_consumo.instituicao_id → instituicoes.tenant_id`
  2) fallback: `sessoes_consumo.mesa_id → mesas.tenant_id`
- valida que não sobra `tenant_id IS NULL`
- aplica `NOT NULL` + `FK (tenant_id → tenants.id)`
- cria índices tenant-scoped

## JPA

### Mesa
- adiciona `@ManyToOne(optional=false) Tenant tenant` mapeado em `tenant_id`.

### SessaoConsumo
- adiciona `@ManyToOne(optional=false) Tenant tenant` mapeado em `tenant_id`.

## Repositories

### MesaRepository
- `findByIdAndTenantId(...)` passa a usar `m.tenant.id`.
- adicionados `findByTenantId(...)` e `countByTenantId(...)`.

### SessaoConsumoRepository
Métodos tenant-scoped adicionados (para fluxos tenant-aware):
- `findByIdAndTenantId(...)`
- `findByTenantIdAndMesaIdAndStatus(...)`
- `findAllByTenantIdAndMesaIdAndStatus(...)`
- `findByTenantIdAndStatus(...)`
- `existsByTenantIdAndMesaIdAndStatus(...)`

## Services ajustados
- `TenantProvisioningService`: ao criar mesas, preenche `mesa.tenant`.
- `MesaService` (admin/legado): ao criar mesa, preenche `mesa.tenant` via `instituicao.tenant`.
- `SessaoConsumoService` (legado): ao abrir/criar sessão automática, preenche `sessao.tenant` via `instituicao.tenant`.
- `PublicQrPedidoService`: reuso/busca de sessão aberta por mesa passa a ser `tenant + mesa + status` e sessão criada passa a ter `tenant` explícito.
- `QrCodeOperacionalService`: valida coerência `mesa.tenant == tenant` ao criar QR.

## Testes
- Adicionado teste de integração validando:
  - mesas persistem com `tenant` direto
  - sessão persistida com `tenant` direto
  - `SessaoConsumoRepository.findAllByTenantIdAndMesaIdAndStatus(...)` isola por tenant

## Próximos passos (não implementados aqui)
- adicionar `tenant_id` direto também em outros agregados operacionais (ex.: `UnidadeAtendimento`, `Cozinha`, etc.) conforme necessidade
- constraints parciais (ex.: “uma sessão ABERTA por (tenant, mesa)”) somente quando a operação estiver estável e os dados forem garantidos
- preparação POS/dispositivo e endpoints de cozinha/subpedido

