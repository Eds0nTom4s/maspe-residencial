# Prompt 46 — Usage Metering e Cobrança por Transação (Billing Core)

Data: 2026-05-25  
Projeto: CONSUMA

## Objetivo
Implementar a base de **metering de uso** e **cobrança interna SaaS** por transação, permitindo:

- registrar eventos de uso idempotentes (usage events);
- agregar uso por ciclo (billing cycle);
- aplicar franquia do plano (included transactions) e excedentes (overage);
- gerar **invoice interna SaaS** (não fiscal) para o tenant;
- auditar e evidenciar no Evidence Bundle sem interferir em pagamentos do cliente final.

## Escopo
- `BillingPlan` (plataforma): preço base + franquia + overage.
- `TenantSubscription`: subscription por tenant com período corrente e anchor day.
- `UsageMetric` / `UsageEvent` idempotente (métrica principal `PAYMENT_CONFIRMED`).
- `UsageAggregation`: soma por período/ciclo, com cálculo de included/overage/charge.
- `BillingCycle`: OPEN → USAGE_FINALIZED → (invoice) (MVP).
- `TenantBillingInvoice` / `TenantBillingInvoiceLine`: invoice interna com numeração sequencial.
- `UsageAdjustment`: ajustes manuais (preparatório) sem alterar o UsageEvent original.
- `billingEvidence` no Evidence Bundle (novo).

## Não escopo
- Não há gateway real para cobrar o tenant (sem débito automático).
- Não há split/settlement de taxas no pagamento do cliente final.
- Não altera AppyPay callback/polling nem confirmação do pagamento.
- Não bloqueia operação do tenant automaticamente por inadimplência.
- Não emite fatura fiscal oficial da CONSUMA ao tenant neste estágio.
- Não recalcula Evidence Bundles antigos.

## Separação: pagamento do cliente final vs cobrança SaaS do tenant
- **Pagamento do cliente final**: `Pagamento`/AppyPay/CASH/TPA; impacta pedido/caixa/fiscalidade/inventário.
- **Cobrança SaaS do tenant**: usage events + agregação + invoice interna; é camada separada.

## Métrica principal (MVP)
- **Billable**: `PAYMENT_CONFIRMED`.
- Tracked (não cobrado por padrão): `FISCAL_DOCUMENT_ISSUED`, `INVENTORY_CONSUMPTION_PROCESSED`, `RETURN_PROCESSED`, etc.

## Idempotência
`UsageEvent` é idempotente por `(tenant_id, idempotency_key)`.

Chave recomendada:
`tenant:{tenantId}:payment:{paymentId}:usage:payment-confirmed:v1`

## Agregação e cálculo de excedentes (MVP)
Para `PAYMENT_CONFIRMED`:

- `included = min(qtyTotal, plan.includedTransactions)`
- `overage = max(0, qtyTotal - included)`
- `usageCharge = overage * plan.overagePricePerTransaction`
- `totalBilling = plan.basePrice + usageCharge` (respeitando `minimumMonthlyFee` se definido)

Trial:
- se subscription `TRIALING`, agrega mas não cobra (`charge=0`).

## Invoice interna SaaS
- A invoice é **interna** e **não fiscal**.
- Numeração interna: `CONS-BILL-YYYY-NNNNNN` (sequência por tenant/ano).
- Invoice não deve ser confundida com `FiscalDocument`.

## Integração com pagamentos (desacoplada)
- O metering reage a pagamento confirmado via listener `AFTER_COMMIT`.
- Falha no metering **não** reverte pagamento (MVP).

## Evidence Bundle
Nova seção: `billingEvidence`, contendo:
- subscription/cycle vigente;
- totais de uso e cálculo (included/overage/base/usage/total);
- invoice (se existir);
- hashes determinísticos (`aggregationHash`, `invoiceHash`);
- warnings: tenant sem subscription, ciclo aberto, ciclo sem invoice, etc.

## Auditoria (sanitizada)
Eventos de auditoria (exemplos):
- `USAGE_EVENT_RECORDED`, `USAGE_EVENT_DUPLICATE_IGNORED`
- `USAGE_AGGREGATION_CREATED`
- `BILLING_CYCLE_OPENED`, `BILLING_CYCLE_USAGE_FINALIZED`
- `TENANT_BILLING_INVOICE_GENERATED`, `TENANT_BILLING_INVOICE_ISSUED`, `...MARKED_PAID`, `...CANCELLED`
- `USAGE_ADJUSTMENT_CREATED`
- `BILLING_EVIDENCE_ATTACHED_TO_BUNDLE`

Nunca auditar payloads sensíveis (AppyPay raw, cartão, tokens, etc).

## Endpoints
Admin/Platform:
- `GET/POST/PUT /admin/billing/plans`
- `GET/POST/PUT /admin/tenants/{tenantId}/billing/subscription`

Tenant:
- `GET /tenant/billing/subscription`
- `GET /tenant/billing/usage/events`
- `GET /tenant/billing/usage/aggregations`
- `POST /tenant/billing/usage/aggregate-current-cycle`
- `GET /tenant/billing/cycles` e `GET /tenant/billing/cycles/{cycleId}`
- `POST /tenant/billing/cycles/{cycleId}/finalize-usage`
- `GET /tenant/billing/invoices` e `GET /tenant/billing/invoices/{invoiceId}`
- `POST /tenant/billing/cycles/{cycleId}/generate-invoice`
- `POST /tenant/billing/invoices/{invoiceId}/mark-paid`
- `POST /tenant/billing/invoices/{invoiceId}/cancel`
- `POST /tenant/billing/usage-adjustments`

## Limitações / Dívidas
- Ajustes automáticos por refund/estorno dependerão de evento financeiro de refund formal.
- Métricas secundárias podem virar cobradas em planos futuros.
- A cobrança real do tenant (pagamento da invoice SaaS) fica para etapa posterior.

## Comandos executados
- `mvn -q -DskipTests compile`
- `mvn test`

## Checklist de aceitação
- [x] BillingPlan / TenantSubscription / UsageEvent / UsageAggregation / BillingCycle
- [x] Invoice interna e numeração sequencial
- [x] PAYMENT_CONFIRMED gera usage event idempotente
- [x] BillingEvidence no Evidence Bundle
- [x] Auditoria sanitizada

