# Prompt 48 — Tenant Billing Payments (Pagamentos de invoice SaaS)

Data: 2026-05-25  
Projeto: CONSUMA

## Objetivo
Fechar o ciclo comercial do **billing SaaS** (Prompt 46) adicionando:
- registro de pagamentos da invoice interna SaaS pelo tenant;
- pagamentos parciais e totais;
- overdue + grace period;
- política de cobrança (collection policy) por tenant;
- base de **suspensão controlada** (decisão/guard), sem bloqueio operacional perigoso;
- evidência no Evidence Bundle (`billingEvidence`) e auditoria sanitizada.

## Escopo implementado
- `TenantBillingPayment` (pagamento de invoice SaaS) com status `RECORDED/CONFIRMED/REJECTED/CANCELLED`.
- `TenantBillingCollectionPolicy` por tenant (defaults seguros; foco em warnings/controle).
- Regras de atualização de `TenantBillingInvoice`:
  - `totalPaidAmount`, `outstandingAmount`, `lastPaymentAt`.
  - status: `ISSUED` → `PARTIALLY_PAID` → `PAID`.
  - detecção de `OVERDUE` (com `overdueAt` + `gracePeriodEndsAt`) sem reverter/alterar pagamentos do cliente final.
- `TenantBillingCollectionService` para avaliar overdue/grace e produzir status consolidado.
- `TenantAccessBillingGuardService` para decisão de acesso por operação (**MVP não bloqueia `CONFIRM_PAYMENT`**).
- Expansão do `billingEvidence` no Evidence Bundle com:
  - rollups de invoices (paid/overdue/outstanding);
  - `collectionStatus` e `gracePeriodEndsAt`;
  - lista de pagamentos (limitada) com `paymentHash`.

## Não escopo (mantido)
- Sem gateway real de cobrança automática do tenant.
- Sem débito automático.
- Sem alteração do fluxo AppyPay/pagamento confirmado do cliente final.
- Sem bloqueio global automático de operações no MVP.
- Sem faturação fiscal oficial da CONSUMA ao tenant.
- Sem recálculo de bundles antigos / sem quebra de WORM.

## Separação de domínios (reforçada)
- **Pagamento do cliente final**: `Pagamento` (CASH/TPA/AppyPay), caixa, fiscalidade, inventário.
- **Pagamento SaaS do tenant**: `TenantBillingPayment` liquidando `TenantBillingInvoice` (invoice interna SaaS).

## Regras principais
### Pagamento de invoice
- Tenant registra pagamento (status `RECORDED`).
- Plataforma confirma/rejeita/cancela pagamento (status `CONFIRMED/REJECTED/CANCELLED`).
- Apenas `CONFIRMED` soma para liquidar invoice.
- **Overpayment** é bloqueado no MVP (valor > `outstandingAmount`).
- Moeda deve coincidir com a invoice.

### Overdue + grace
- Invoice com `dueAt < now` e `outstandingAmount > 0` pode virar `OVERDUE`.
- `gracePeriodEndsAt = overdueAt + gracePeriodDays` (policy).
- `collectionStatus` é calculado a partir do estado do tenant e invoices vencidas.

### Suspensão controlada (guard)
- Guard retorna decisão (`allowed`, `warningOnly`, `messageCode`) por tipo de operação.
- **No MVP, `CONFIRM_PAYMENT` não é bloqueado**.
- Aplicação de bloqueios deve ser gradual e limitada a operações não críticas (ex.: `ADD_DEVICE`).

## Endpoints
Tenant (TENANT_OWNER/TENANT_ADMIN/TENANT_FINANCE):
- `GET  /tenant/billing/invoices/{invoiceId}/payments`
- `POST /tenant/billing/invoices/{invoiceId}/payments`
- `GET  /tenant/billing/payments/{paymentId}`
- `GET  /tenant/billing/collection-status`

Admin/Platform (PLATFORM_ADMIN):
- `GET  /admin/billing/payments`
- `GET  /admin/billing/payments/{paymentId}`
- `POST /admin/billing/payments/{paymentId}/confirm`
- `POST /admin/billing/payments/{paymentId}/reject`
- `POST /admin/billing/payments/{paymentId}/cancel`
- `POST /admin/billing/invoices/{invoiceId}/mark-overdue`
- `POST /admin/billing/tenants/{tenantId}/evaluate-collection`
- `GET  /admin/billing/tenants/{tenantId}/collection-status`
- `GET  /admin/billing/tenants/{tenantId}/collection-policy`
- `PUT  /admin/billing/tenants/{tenantId}/collection-policy`

## Evidência (Evidence Bundle)
`billingEvidence` agora pode incluir:
- totais de invoices (paid/overdue/outstanding) e valores agregados;
- `collectionStatus` + `gracePeriodEndsAt`;
- `billingPayments` com `paymentHash`;
- warnings: overdue, pagamento pendente de confirmação, pagamento rejeitado, pagamento parcial.

## Auditoria (sanitizada)
Eventos adicionados (exemplos):
- `TENANT_BILLING_PAYMENT_RECORDED / CONFIRMED / REJECTED / CANCELLED`
- `TENANT_BILLING_INVOICE_PARTIALLY_PAID / PAID / OVERDUE`
- `TENANT_BILLING_COLLECTION_STATUS_CHANGED`
- `TENANT_BILLING_PAYMENT_EVIDENCE_ATTACHED_TO_BUNDLE`

Sem log de comprovativos brutos/payload sensível.

## Prompt 47 — Transaction Evidence Ledger
Pagamentos confirmados de billing SaaS (`TenantBillingPayment CONFIRMED`) podem ser evidenciados individualmente no Transaction Evidence Ledger (Prompt 47), mantendo o Evidence Bundle como prova consolidada e o ledger como prova por evento.

## Limitações / Dívidas futuras
- Refund/estorno de billing SaaS (pagamento revertido) não foi implementado no MVP.
- Integração com gateway B2B (ex.: AppyPay business) permanece placeholder.
- Aplicação global do guard em endpoints críticos deve ser tratada como rollout controlado (feature flag/policy).

## Comandos executados
- `mvn -q -DskipTests compile`
- `mvn test`
