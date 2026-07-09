# Prompt 38.2-FIX — Matriz de Testes de Integração (Policies por Unidade/Device)

## Objetivo

Fechar a fase 38.2 provando, com testes de integração (PostgreSQL/Testcontainers), que a política hierárquica de métodos de pagamento funciona em fluxo real:

`TenantPaymentMethod → UnidadePaymentMethodPolicy → DevicePaymentMethodPolicy`

Sem adicionar novas funcionalidades de produto.

## O que foi coberto (IT)

### Admin — Unidade

- CRUD via endpoints:
  - `GET /tenant/unidades/{unidadeId}/payment-method-policies`
  - `GET /tenant/unidades/{unidadeId}/payment-method-policies/{code}`
  - `PUT /tenant/unidades/{unidadeId}/payment-method-policies/{code}`
  - `DELETE /tenant/unidades/{unidadeId}/payment-method-policies/{code}`
- Validações:
  - `minAmount/maxAmount` negativos e `max < min` retornam erro.
- Cross-tenant:
  - leitura e escrita em unidade de outro tenant retornam `404`.

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodUnitPolicyIT.java`

### Admin — Device

- CRUD via endpoints:
  - `GET /tenant/devices/{deviceId}/payment-method-policies`
  - `GET /tenant/devices/{deviceId}/payment-method-policies/{code}`
  - `PUT /tenant/devices/{deviceId}/payment-method-policies/{code}`
  - `DELETE /tenant/devices/{deviceId}/payment-method-policies/{code}`
- Validações:
  - `minAmount/maxAmount` negativos e `max < min` retornam erro.
- Cross-tenant:
  - leitura e escrita em device de outro tenant retornam `404`.

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodDevicePolicyIT.java`

### RBAC admin (policies)

- `TENANT_OPERATOR` / `TENANT_KITCHEN`: bloqueados (403) em listagem admin.
- `TENANT_CASHIER`: read-only (PUT/DELETE bloqueados).

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodPolicyAdminRbacIT.java`

### Herança e remoção de override

- Sem policy explícita, QR herda as regras do tenant.
- `UnidadePolicy BLOCK` remove método do QR.
- Remoção da policy restaura herança do tenant.

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodPolicyInheritanceIT.java`

### Disponibilidade por device/POS

- `/device/payment-methods` respeita:
  - bloqueio na unidade (`BLOCK`);
  - restrições no device (`canConfirmManual=false`).

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodPolicyDeviceMethodsIT.java`

### Fluxo manual CASH (criação/confirmação)

- Ordem criada enquanto permitido pode ser **bloqueada na confirmação** se a unidade passar a bloquear o método.
- Confirmação manual falha quando `canConfirmManual=false` no device.

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodPolicyManualPaymentIT.java`

### AppyPay (policy) + não dependência pós-iniciação

- POS não inicia AppyPay quando `canStartGateway=false` no device.
- Polling manual confirma pagamento já iniciado mesmo que a unidade bloqueie AppyPay depois (policy não é usada para bloquear confirmação retroativa).

Arquivo: `src/test/java/com/restaurante/financeiro/PaymentMethodPolicyAppyPayIT.java`

## Correções pontuais (para suportar IT)

- `UnidadePaymentMethodPolicyAdminService` e `DevicePaymentMethodPolicyAdminService` passam a validar `minAmount/maxAmount` (negativo e `max < min`) antes de persistir, gerando erro de negócio consistente (em vez de depender apenas do constraint do banco).

Arquivos:
- `src/main/java/com/restaurante/financeiro/paymentmethod/service/UnidadePaymentMethodPolicyAdminService.java`
- `src/main/java/com/restaurante/financeiro/paymentmethod/service/DevicePaymentMethodPolicyAdminService.java`

## Limitações conhecidas (desta fix)

- Não cobre callback AppyPay end-to-end (o foco é garantir que confirmação/polling não dependa de policy após iniciação).
- Regressões financeiras (snapshot/evidence bundle/WORM) continuam sendo garantidas pelos suites já existentes; esta fix adiciona cobertura focada em policies unidade/device.

## Execução

Os testes foram desenhados para rodar com PostgreSQL via Testcontainers no profile `it-postgres` e podem ser skipados automaticamente quando Docker não estiver disponível no ambiente de execução.

