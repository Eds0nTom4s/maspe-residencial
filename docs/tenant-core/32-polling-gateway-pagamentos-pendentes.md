# Prompt 32 — Polling ativo do gateway para pagamentos pendentes

## Objetivo

Adicionar um mecanismo complementar de resiliência para confirmação de pagamentos pendentes quando o callback AppyPay atrasar, falhar ou não chegar.

- Callback continua sendo a via principal.
- Polling consulta o gateway para pagamentos **pendentes** e aplica a confirmação de forma **idempotente**.

## Conceitos

- **Pagamento pendente**: `StatusPagamentoGateway.PENDENTE`
- **Evidência do gateway**: resposta do AppyPay (`getCharge`) indicando `CONFIRMED`
- **Convergência**: callback e polling usam o mesmo serviço central de confirmação.

## Migração

- `src/main/resources/db/migration/V23__pagamento_gateway_polling.sql`
  - adiciona campos e índices de controle do polling em `pagamentos_gateway`

Campos (resumo):
- `polling_enabled`, `polling_attempts`
- `last_polling_attempt_at`, `next_polling_attempt_at`
- `polling_status`, `polling_last_error_*`
- `gateway_status_last_checked_at`, `gateway_status_raw`, `expires_at`

## Propriedades

Em `src/main/resources/application.properties`:

- `consuma.payment.polling.enabled=true`
- `consuma.payment.polling.fixed-delay-ms=60000`
- `consuma.payment.polling.batch-size=50`
- `consuma.payment.polling.max-attempts=10`
- `consuma.payment.polling.initial-delay-minutes=2`
- `consuma.payment.polling.max-age-hours=24`
- `consuma.payment.polling.backoff-multiplier=2`
- `consuma.payment.polling.max-backoff-minutes=30`

Em testes (`src/test/resources/application-*.properties`), o polling fica desligado por default para evitar interferência, e testes específicos habilitam via `@SpringBootTest(properties=...)`.

## Arquitetura

### Consulta de status

- Porta: `com.restaurante.financeiro.polling.PaymentGatewayStatusPort`
- Adapter AppyPay: `com.restaurante.financeiro.polling.AppyPayStatusAdapter`
  - usa `AppyPayClient.getCharge(chargeId)`

### Serviço central de confirmação

- `com.restaurante.financeiro.polling.PagamentoConfirmacaoService`
  - aplica confirmação idempotente para pagamentos `POS_PAGO` vinculados a `Pedido`
  - valida divergência de valor (centavos) quando disponível

### Polling

- `com.restaurante.financeiro.polling.PagamentoGatewayPollingService`
  - seleciona pagamentos elegíveis
  - aplica backoff simples
  - confirma via `PagamentoConfirmacaoService` ao receber `CONFIRMED`

### Job

- `com.restaurante.financeiro.polling.PagamentoGatewayPollingJob`
  - `@Scheduled`
  - ativo apenas quando `consuma.payment.polling.enabled=true`

## Concorrência callback vs polling

- Callback e polling podem atuar no mesmo pagamento.
- O repositório usa lock pessimista por `Pagamento.id` (`findForUpdateById`) para serializar alterações.
- Se já confirmado, polling ignora; callback duplicado permanece idempotente.

## Auditoria operacional

Eventos em `OperationalEventLog` (actorType=SYSTEM):

- `PAGAMENTO_POLLING_TENTADO`
- `PAGAMENTO_CONFIRMADO_POR_POLLING`
- `PAGAMENTO_POLLING_PENDENTE`
- `PAGAMENTO_POLLING_FALHOU`
- `PAGAMENTO_POLLING_EXPIRADO`
- `PAGAMENTO_POLLING_MAX_TENTATIVAS`
- `PAGAMENTO_POLLING_DIVERGENCIA_VALOR`

## Limitações (intencionais)

- Não é conciliação bancária final.
- Não implementa refund/cancelamento.
- Não substitui callback.
- Backoff é simples (melhorias futuras: locks distribuídos, ShedLock/advisory lock, políticas por tenant/plano).

