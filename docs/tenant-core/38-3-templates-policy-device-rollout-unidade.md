# Prompt 38.3 — Templates de policy por tipo de device + rollout por unidade

## 1) Objetivo

Reduzir risco operacional e repetição na configuração de `DevicePaymentMethodPolicy` ao introduzir **templates reutilizáveis** e **rollout controlado por unidade**, mantendo a resolução efetiva existente:

`TenantPaymentMethod` → `UnidadePaymentMethodPolicy` → `DevicePaymentMethodPolicy`

O template **não é** um método de pagamento. Ele apenas facilita gerar/atualizar policies no nível **device**.

## 2) Diferença: policy manual vs template

- **Policy manual**: criada/alterada via endpoints existentes de policy por device.
- **Policy via template (template-managed)**: criada/atualizada por rollout e marcada com:
  - `templateManaged=true`
  - `manualOverride=false`
  - `sourceTemplateId`, `sourceRolloutId`, `templateAppliedAt`

Se uma policy for alterada manualmente via endpoint normal, ela passa a:

- `manualOverride=true`
- `templateManaged=false`

Assim, `OVERWRITE_ONLY_TEMPLATE_MANAGED` não sobrescreve ajustes manuais.

## 3) Templates padrão (bootstrap)

No primeiro acesso a `/tenant/payment-policy-templates`, se o tenant não tiver templates, são bootstrapados:

- `POS_CAIXA_COMPLETO`
- `POS_ATENDIMENTO_SEM_CASH`
- `KDS_SEM_PAGAMENTO`
- `QUIOSQUE_APPYPAY`

Os defaults são tenant-owned e `isSystemDefault=true` (MVP: editáveis pelo tenant).

## 4) Rollout (unidade)

O rollout aplica os itens do template como `DevicePaymentMethodPolicy` para devices-alvo da unidade.

Modos:

- `UNIT_ALL_DEVICES`: todos os devices da unidade
- `UNIT_BY_DEVICE_TYPE`: devices da unidade filtrados por `OperationalDeviceType`
- `SELECTED_DEVICES`: lista explícita de `deviceIds` (validada contra tenant + unidade)

## 5) Preview / Dry-run

`/rollout/preview`:

- calcula devices-alvo
- calcula políticas a criar/atualizar/ignorar
- **não persiste** nada
- registra auditoria sanitizada (sem secrets/credenciais/deviceToken)

## 6) Apply

`/rollout/apply`:

- valida tenant/template/unidade
- seleciona devices-alvo
- persiste `payment_method_policy_rollouts`
- cria/atualiza `device_payment_method_policies` conforme `overwriteMode`
- marca policies como template-managed e linka `sourceTemplateId/sourceRolloutId`
- transação única (falha → rollback total) e evento `PAYMENT_POLICY_ROLLOUT_FAILED`

## 7) Overwrite modes

- `SKIP_EXISTING`: se já existe policy para (device, method) → ignora
- `OVERWRITE_EXISTING`: sempre atualiza policy existente
- `OVERWRITE_ONLY_TEMPLATE_MANAGED`: atualiza somente se `templateManaged=true` e `manualOverride=false`

## 8) Proteção de overrides manuais

Qualquer alteração via endpoint manual de policy por device marca:

- `manualOverride=true`
- `templateManaged=false`

Isso evita que rollouts com `OVERWRITE_ONLY_TEMPLATE_MANAGED` sobreponham ajustes explícitos.

## 9) Segurança / RBAC

- Tenant sempre vem do `TenantContext` (não há `tenantId` no payload)
- Template/unidade/device cross-tenant retornam `404`/erro
- `OWNER/ADMIN/FINANCE`: CRUD + apply
- `CASHIER`: read-only (list/get/preview)
- `OPERATOR/KITCHEN`: bloqueados

## 10) Limitações (MVP)

- Sem UI
- Sem rollout assíncrono
- Sem import/export de templates
- Sem partial apply complexo (rollback total em erro)
- Não altera callback/polling AppyPay e não altera WORM/evidence bundle

## 11) Próximo passo recomendado

- Rollout assíncrono com status progressivo (para unidades com muitos devices)
- Export/import de templates por tenant
- Regras de “lock” para templates system default (se necessário)

