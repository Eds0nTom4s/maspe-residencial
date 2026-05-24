# Prompt 43.1 — Automatização fiscal controlada pós-pagamento

## Objetivo
Permitir **auto-emissão controlada** do **documento fiscal interno** após **pagamento confirmado**, sem acoplar a confirmação do pagamento à emissão fiscal.

Regras centrais:
- pagamento confirmado **não é revertido** por falha fiscal (MVP);
- emissão fiscal roda como consequência (job/worker), com **idempotência e retry**;
- **não altera AppyPay callback/polling** estruturalmente (apenas reage a “pagamento confirmado”);
- Evidence Bundle continua preservando WORM/hash/HMAC/chainHash e compatibilidade com bundles antigos.

## Escopo implementado
- Fila persistida: `FiscalAutoIssueJob`.
- Criação de job pós-confirmação via evento `PaymentConfirmedForFiscalIssueEvent` (AFTER_COMMIT).
- Worker com lock lógico, backoff e maxAttempts:
  - `FiscalAutoIssueWorker`.
- Classificação básica de falhas:
  - `FiscalAutoIssueFailureClassifier`.
- Endpoints tenant/admin para listagem e operações de jobs.
- Endpoint device somente leitura para status por pagamento.
- `taxEvidence` atualizado com métricas de auto-emissão e warning para pagamentos confirmados sem documento.

## O que NÃO foi implementado
- Reverter pagamento em caso de erro fiscal (`fail-payment-on-tax-error=false` no MVP).
- UI de backoffice.
- Emissão offline (numeração local).
- Integração fiscal oficial/certificada.

## Modelo de dados
Tabela: `fiscal_auto_issue_jobs`

Estados principais (`FiscalAutoIssueJobStatus`):
- `PENDING`, `PROCESSING`, `ISSUED`
- `FAILED_RETRYABLE`, `FAILED_PERMANENT`
- `CANCELLED`, `SKIPPED`

Idempotência:
- `uk (tenant_id, idempotency_key)`
- documento fiscal também é idempotente por pagamento (não duplica por `pagamento_id`).

## Configurações
Propriedades relevantes (prefixo `consuma.tax.document.auto-issue.*`):
- `enabled`
- `max-attempts`
- `initial-delay-seconds`
- `retry-backoff-seconds`
- `max-backoff-seconds`
- `batch-size`
- `stale-lock-minutes`
- `worker-id`

E o toggle de criação automática pós-pagamento:
- `consuma.tax.document.auto-issue-on-payment`

## Fluxo pós-pagamento
1. Pagamento é confirmado (manual CASH/TPA ou gateway AppyPay).
2. Service publica `PaymentConfirmedForFiscalIssueEvent`.
3. Listener (AFTER_COMMIT) valida elegibilidade e cria `FiscalAutoIssueJob` (idempotente).
4. Worker coleta jobs “due”, faz claim lock e tenta emitir documento:
   - sucesso → `ISSUED` + `fiscalDocumentId`;
   - falha retryable → `FAILED_RETRYABLE` + `nextAttemptAt`;
   - falha permanente/maxAttempts → `FAILED_PERMANENT`.

## Endpoints
Tenant/admin:
- `GET /tenant/fiscal/auto-issue/jobs`
- `GET /tenant/fiscal/auto-issue/jobs/{jobId}`
- `POST /tenant/fiscal/auto-issue/jobs/{jobId}/retry`
- `POST /tenant/fiscal/auto-issue/jobs/{jobId}/cancel`
- `POST /tenant/fiscal/auto-issue/issue-for-payment/{pagamentoId}`

Device (somente leitura):
- `GET /device/fiscal/issue-status/pagamento/{pagamentoId}`

## Evidence Bundle / taxEvidence
`taxEvidence` inclui:
- `totalAutoIssueJobs`, `pendingAutoIssueJobs`, `failedPermanentAutoIssueJobs`, etc.
- `confirmedPaymentsWithoutFiscalDocument`
- warnings:
  - `CONFIRMED_PAYMENT_WITHOUT_FISCAL_DOCUMENT`
  - `AUTO_ISSUE_JOB_FAILED_PERMANENT`
  - `AUTO_ISSUE_JOB_FAILED_RETRYABLE`

Bundles antigos continuam legíveis sem esses campos.

## Auditoria
Eventos operacionais adicionados:
- `FISCAL_AUTO_ISSUE_JOB_CREATED`
- `FISCAL_AUTO_ISSUE_JOB_PROCESSING_STARTED`
- `FISCAL_AUTO_ISSUE_JOB_ISSUED`
- `FISCAL_AUTO_ISSUE_JOB_FAILED_RETRYABLE`
- `FISCAL_AUTO_ISSUE_JOB_FAILED_PERMANENT`
- `FISCAL_AUTO_ISSUE_JOB_RETRIED_MANUALLY`
- `FISCAL_AUTO_ISSUE_JOB_CANCELLED`
- `FISCAL_AUTO_ISSUE_STALE_LOCK_RECOVERED`
- `FISCAL_AUTO_ISSUE_MANUAL_TRIGGERED`

Sanitização: não incluir token de device, payload bruto de gateway, dados sensíveis, segredos.

## Limitações e próximos passos recomendados
- Evoluir classificador de falhas (diferenciar melhor transient vs. permanent).
- Backfill controlado para pagamentos confirmados antigos sem documento.
- Painel/relatórios de pendências fiscais (UI) em prompt futuro.

