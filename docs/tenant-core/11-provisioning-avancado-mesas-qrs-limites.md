# Prompt 11 — Provisionamento avançado (mesas, QR por mesa e limites)

Data: 2026-05-16

## Objetivo

Evoluir o provisionamento manual administrado (Prompt 10) para suportar:

- criação opcional de **mesas**
- criação opcional de **QR por mesa** (QrCodeOperacional tipo `MESA`)
- enforcement de limites do plano/override durante o provisionamento:
  - `maxUnidadesAtendimento`
  - `maxUsuarios` (TenantUsers)
  - `maxQrCodes` (QrCodeOperacional)

Tudo de forma **atômica** (rollback total em qualquer falha/limite excedido).

## Templates

Os templates passaram a incluir no `configuracao_json`:

- `criarMesas`
- `quantidadeMesas`
- `prefixoMesa`
- `criarQrPorMesa`

Migração:

- `V12__update_provisioning_templates_advanced.sql`

Nota: para o Plano `PILOTO` (maxQrCodes=10), o template `RESTAURANTE_SIMPLES` foi ajustado para:

- 9 mesas + 1 QR principal = 10 QRs

Para pilotos com 10+ mesas, usar `limitesOverride.maxQrCodes`.

## Limites (enforcement)

`TenantLimitService` agora valida também:

- `assertCanCreateUnidadeAtendimento(tenantId, quantidadeNova)`
- `assertCanCreateUser(tenantId, quantidadeNova)`
- `assertCanCreateQrCode(tenantId, quantidadeNova)`

No provisionamento, calculamos quantos recursos serão criados (incluindo QR por mesa) e validamos antes de persistir recursos operacionais.

## Mesas e QR por mesa

- Mesas são criadas em `mesas` vinculadas à `UnidadeAtendimento` e `Instituicao`.
- Campo legado `Mesa.qr_code` não é usado (permanece `null`).
- QRs por mesa são criados em `qr_codes_operacionais` e resolvem tenant/instituição/unidade/mesa.

## Override de limites no request

Para suportar pilotos acima do plano, o request de provisionamento aceita:

- `limitesOverride.maxQrCodes`, `maxUsuarios`, `maxUnidadesAtendimento`, etc.

Isso cria um `TenantLimiteOverride` ativo para o tenant no mesmo provisionamento (uso estritamente administrativo).

## Próximos passos

- preview endpoint (dry-run) para calcular recursos/limites antes de provisionar
- provisionamento de cozinhas/unidades de produção e rotas
- criação opcional de QR por unidade vs por mesa conforme operação

