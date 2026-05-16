# Prompt 12 — Preview (Dry-run) de Provisionamento + Hardening de Erros

## Objetivo
Adicionar um endpoint **platform-admin** para **pré-visualizar** (dry-run) o provisionamento antes de executar, com:
- cálculo dos recursos que seriam criados (tenant, instituição, unidade, mesas, QR etc.)
- validação antecipada de **plano/template/slug/tenantCode/opções/limites**
- retorno de **bloqueios** e **avisos** com códigos estáveis
- garantia de **zero persistência** no preview

## Endpoints
- `POST /platform/tenants/provisionar/preview` (PLATFORM_ADMIN)
  - Não persiste nada.
  - Retorna `permitido`, `bloqueios`, `avisos`, plano de recursos e visão de limites.

O endpoint real permanece:
- `POST /platform/tenants/provisionar` (PLATFORM_ADMIN)

## Motor compartilhado (evitar divergência)
Foi introduzido um motor de cálculo sem persistência:
- `ProvisioningPlanCalculator`

Ele é usado por:
- `TenantProvisioningPreviewService` (preview)
- `TenantProvisioningService` (execução real)

Assim, **template + overrides do request** geram o mesmo plano em preview e execução real.

## Limites e validações aplicadas no preview
O preview calcula:
- QR codes necessários (`QR principal` + `QR por mesa`, se habilitado)
- unidades de atendimento necessárias
- tenant users necessários (OWNER)
- instituições necessárias (sempre 1 neste fluxo)

E compara com limites efetivos simulados:
- Plano + `limitesOverride` do request (quando enviado)

Quando excede:
- retorna `permitido=false`
- adiciona bloqueio com código (ex.: `MAX_QR_CODES_EXCEDIDO`)

## Padronização de erros no endpoint real
Erros operacionais do provisionamento (slug duplicado, limites excedidos etc.) agora usam:
- `ProvisioningException`
- `ProvisioningErrorResponse` no `GlobalExceptionHandler`

Isso evita depender de mensagens de SQL/constraints e fornece `code` + `field` estáveis.

## Segurança
- Preview e provisionamento real continuam restritos a `PLATFORM_ADMIN` via `TenantGuard.assertPlatformAdmin()`.
- Preview não expõe dados sensíveis de usuários existentes (apenas sinaliza reutilização).

## Testes
Foram adicionados testes de integração (PostgreSQL/Testcontainers) para:
- preview permitido e **não persistente**
- bloqueio por `maxQrCodes` e liberação via override
- acesso negado para não-platform
- erro padronizado (`409`) no endpoint real quando limite é excedido

## Próximo passo sugerido
Adicionar uma UI/admin console (fora do backend) consumindo:
- `POST /platform/tenants/provisionar/preview`
- `POST /platform/tenants/provisionar`

