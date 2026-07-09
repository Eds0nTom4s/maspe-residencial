# Tenant Core — Limites por Tenant/Plano (Prompt 3)

Data: 2026-05-14

## Objetivo

Implementar enforcement inicial de limites por tenant, calculando limites efetivos a partir de:
1) Subscrição ATIVA do tenant
2) Plano associado
3) Override ativo (se existir)

Nesta fase:
- enforcement inicial para criação de `Instituicao` (maxInstituicoes)
- sem TenantContext/TenantGuard ainda (tenantId é passado explicitamente)

## Regra de cálculo (efetivo)

Para cada limite:
- se override tiver valor não-null → usar override
- senão → usar valor do plano

## Comportamento sem subscrição ativa

- Bloqueia criação de recursos:
  - exceção: "Tenant não possui subscrição ativa."

## Estado do tenant

- Apenas `ATIVO` pode criar recursos.
- `RASCUNHO/SUSPENSO/BLOQUEADO/CANCELADO` bloqueia:
  - exceção: "Tenant não está ativo para criação de recursos."

## Implementação

### `TenantLimitService`
- `getEffectiveLimits(tenantId)`
- `assertCanCreateInstituicao(tenantId)`

### Integração inicial

- `InstituicaoService.criarInstituicao(tenantId, ...)` chama `TenantLimitService.assertCanCreateInstituicao`.

Observação:
- Fluxos legados (instituição ativa global) permanecem intactos nesta fase.

## Testes

- Unit test (Mockito): `TenantLimitServiceTest`
- Integração (PostgreSQL/Testcontainers): extensão em `TenantCorePersistenceIT` validando:
  - plano PILOTO limita para 1
  - override maxInstituicoes=3 permite até 3

## Próximo passo

Prompt 4 (futuro):
- introduzir TenantContext/TenantResolver/TenantGuard
- começar a tornar queries/serviços tenant-aware por request

