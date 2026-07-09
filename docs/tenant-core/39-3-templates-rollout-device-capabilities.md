# Prompt 39.3 — Templates + rollout de device capabilities por OperationalDeviceType/unidade

## Objetivo
Reduzir configuração manual de capabilities em ambientes com muitos devices (POS/KDS/quiosques), permitindo padronização por tipo e rollout controlado por unidade.

## Separação de camadas
- **Payment Policy Templates (Prompt 38.x):** governam métodos de pagamento disponíveis.
- **Device Capability Templates (Prompt 39.3):** governam ações operacionais autorizadas no device (lookup/OTP assistido/link, etc.).

Não há integração entre esses rollouts nesta fase.

## Modelo
- `device_capability_templates` + `device_capability_template_items`
- `device_capability_rollouts` (histórico síncrono)
- Evolução de `device_operational_capabilities` com:
  - `source_template_id`, `source_rollout_id`
  - `template_managed`, `manual_override`
  - `template_applied_at`

## Templates padrão
Criados por tenant (idempotente):
- `CAP_POS_CAIXA_PADRAO`
- `CAP_POS_ATENDIMENTO_PADRAO`
- `CAP_POS_QUIOSQUE_PADRAO`
- `CAP_KDS_COZINHA_SEM_IDENTIFICACAO`
- `CAP_KDS_BAR_SEM_IDENTIFICACAO`
- `CAP_ADMIN_TERMINAL_CONTROLADO`

Regra: `CROSS_UNIT_ASSISTED_IDENTIFICATION` nunca é incluída nos defaults.

## Endpoints (tenant/admin)
Base: `/tenant/device-capability-templates`
- `GET /` list
- `GET /{templateId}` detail
- `POST /` create
- `PUT /{templateId}` update
- `POST /{templateId}/activate`
- `POST /{templateId}/deactivate`
- `POST /{templateId}/rollout/preview`
- `POST /{templateId}/rollout/apply`

RBAC:
- OWNER/ADMIN: full
- FINANCE/CASHIER: list/detail/preview
- OPERATOR/KITCHEN: bloqueados (por TenantGuard)

## Preview (dry-run)
- Calcula devices alvo por:
  - todos da unidade
  - por `OperationalDeviceType`
  - lista explícita (`selectedDeviceIds`)
- Retorna contagem de create/update/skip e resultados por device.
- Não persiste capabilities.

## Apply (síncrono)
- Aplica em transação única (MVP):
  - cria/atualiza `device_operational_capabilities` conforme overwrite mode
  - grava `sourceTemplateId`, `sourceRolloutId`, `templateManaged=true`, `manualOverride=false`, `templateAppliedAt`
- Registra rollout em `device_capability_rollouts`.

## Overwrite modes
- `SKIP_EXISTING`: não altera capability existente
- `OVERWRITE_EXISTING`: sobrescreve existente
- `OVERWRITE_ONLY_TEMPLATE_MANAGED`: apenas se `templateManaged=true` e `manualOverride=false`

## Proteção de manualOverride
- Alteração manual via `/tenant/devices/{deviceId}/capabilities/{capability}` marca:
  - `manualOverride=true`, `templateManaged=false`
  - limpa `sourceTemplateId/sourceRolloutId/templateAppliedAt`
- Rollout em `OVERWRITE_ONLY_TEMPLATE_MANAGED` não pisa override humano.

## Auditoria
Eventos:
- template CRUD + defaults bootstrapped
- rollout preview/applied/failed
Metadados sanitizados (sem OTP/otpHash/deviceToken).

## Limitações
- Rollout é síncrono (não assíncrono).
- Sem templates de capabilities por rollout assíncrono/multiworker.
- Sem UI.

## Próximo passo recomendado
Prompt 39.4: rollout assíncrono de capabilities (similar ao 38.4), se o volume de devices justificar.

