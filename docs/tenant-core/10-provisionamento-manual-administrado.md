# Prompt 10 — Provisionamento manual administrado (PLATFORM_ADMIN)

Data: 2026-05-16

## Objetivo

Permitir que um **PLATFORM_ADMIN** da CONSUMA crie manualmente um tenant piloto com infraestrutura operacional mínima pronta, sem onboarding público self-service.

Entrega:

- templates de provisionamento (seed)
- endpoint platform para provisionar
- criação atômica/transacional dos recursos mínimos
- aplicação de limites (via `TenantLimitService`)

## O que esta fase cria

Ao provisionar:

1. `Tenant`
2. `Subscricao` ATIVA (Plano escolhido)
3. `Instituicao` (1ª operação do tenant)
4. `UnidadeAtendimento` padrão (conforme template)
5. `CategoriaProduto` default (`geral`)
6. `User` responsável (opcional) + `TenantUser` OWNER
7. `QrCodeOperacional` principal

## O que esta fase NÃO cria

- onboarding público automático
- billing/subscrição paga
- ativação por email/SMS
- POS/dispositivos
- criação avançada de mesas e QR por mesa

## Templates

Tabela: `provisioning_templates`

Templates seedados:

- `VENDEDOR_RUA`
- `RESTAURANTE_SIMPLES`
- `BAR`
- `LOJA`
- `EVENTO`

Os templates possuem `configuracao_json` (uso incremental) e são selecionados no request por `templateCodigo`.

## Endpoint platform

Base: `/api/platform/tenants`

- `POST /provisionar`
- `GET /templates`

Observação: exige `PLATFORM_ADMIN` (validação via `TenantGuard.assertPlatformAdmin()`).

## Consistência transacional

Provisionamento é atômico:

- qualquer falha (ex.: UNIQUE violation de `Instituicao.sigla`) faz rollback completo
- não deixa tenant/subscrição “pendurado” pela metade

## Limites por plano

Antes de criar `Instituicao`, chamamos:

- `TenantLimitService.assertCanCreateInstituicao(tenantId)`

Nota: `TenantLimitService` bloqueia criação quando `Tenant != ATIVO`. Por isso o provisionamento cria tenant como `ATIVO` inicialmente e, se a opção `ativarTenant=false` for usada, rebaixa o tenant para `RASCUNHO` ao final.

## QR principal

O QR principal é criado via `QrCodeOperacionalService.criarQr(...)` e retorna:

- token público aleatório (não enumerável)
- URL pública sugerida via `consuma.public-base-url` + `/q/{token}` (rota de frontend)

## Próximas fases

- provisionamento com múltiplas instituições/unidades (enforcement por plano)
- criação opcional de mesas + QR por mesa
- provisioning de cozinhas/unidades de produção
- ativação/entrega segura de credenciais (email/SMS)

