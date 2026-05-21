# Prompt 38.2 — Política de métodos de pagamento por unidade/device

## Objetivo
Evoluir a configuração de métodos de pagamento de **tenant-level** para uma política hierárquica:

`TenantPaymentMethod` → `UnidadePaymentMethodPolicy` → `DevicePaymentMethodPolicy`

Permitindo que o tenant restrinja métodos por **unidade de atendimento** e por **device/POS**, mantendo AppyPay opcional e CASH/TPA operacionais sem gateway.

## Regra de herança
- **Sem policy na unidade:** herda do tenant.
- **Sem policy no device:** herda da unidade.
- Policies inferiores **não podem expandir permissões** acima do tenant:
  - unidade/device não “reativam” método `INACTIVE/SUSPENDED` do tenant;
  - unidade/device não “ligam” QR/POS/PEDIDO/FUNDO_CONSUMO se o tenant já desabilitou.

## Bloqueios
- `BLOCK` e `SUSPENDED` na unidade/device **bloqueiam** o método nesse nível (e abaixo).
- `ALLOW` apenas confirma permissões **dentro** dos limites do nível superior.

## Limites (min/max)
Os limites efetivos são a **interseção**:
- `minEffective` = maior `min` entre tenant/unidade/device
- `maxEffective` = menor `max` entre tenant/unidade/device

Se a interseção ficar inválida (`max < min`), o método fica indisponível.

## QR vs POS
- **QR público** usa política do **tenant + unidade** (o QR resolve unidade via token).
- **POS/device** usa política do **tenant + unidade + device**.

## Manual vs Gateway
- CASH/TPA (manual):
  - confirmação manual no POS passa a validar também:
    - política da unidade/device;
    - `canConfirmManual` no device quando configurado.
- APPYPAY (gateway):
  - iniciação de pagamento passa a validar também:
    - política da unidade/device;
    - `canStartGateway` no device quando configurado.
- Callback/polling não são alterados: pagamentos já iniciados continuam confirmando mesmo após mudanças de policy.

## Endpoints admin
- Unidade:
  - `GET /tenant/unidades/{unidadeId}/payment-method-policies`
  - `GET /tenant/unidades/{unidadeId}/payment-method-policies/{code}`
  - `PUT /tenant/unidades/{unidadeId}/payment-method-policies/{code}`
  - `DELETE /tenant/unidades/{unidadeId}/payment-method-policies/{code}`

- Device:
  - `GET /tenant/devices/{deviceId}/payment-method-policies`
  - `GET /tenant/devices/{deviceId}/payment-method-policies/{code}`
  - `PUT /tenant/devices/{deviceId}/payment-method-policies/{code}`
  - `DELETE /tenant/devices/{deviceId}/payment-method-policies/{code}`

RBAC:
- OWNER / ADMIN / FINANCE: alteram
- CASHIER: read-only
- OPERATOR / KITCHEN: bloqueados

## Limitações
- Não cria UI.
- Não cria novo gateway.
- Não implementa refund/settlement/fiscalidade.
- Não altera callback/polling AppyPay.

## Próximo passo recomendado
Prompt 38.3: políticas por “tipo de device” (POS/KDS) e templates de policy para rollout rápido.

