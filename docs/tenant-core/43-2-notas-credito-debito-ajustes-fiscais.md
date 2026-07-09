# Prompt 43.2 — Notas de crédito/débito internas e impacto fiscal de ajustes

## Objetivo
Fechar a ponte entre **ajustes operacionais aprovados** (Prompt 42.2) e a **base fiscal interna** (Prompt 43/43.1), sem editar o `FiscalDocument` original.

O Prompt 43.2 introduz:
- **avaliação formal** do impacto fiscal de um `CaixaOperadorAdjustment` aprovado;
- emissão de **documento corretivo interno**:
  - `INTERNAL_CREDIT_NOTE` (reduz base/imposto/total),
  - `INTERNAL_DEBIT_NOTE` (aumenta base/imposto/total),
- integração no `taxEvidence` do Evidence Bundle (sem recalcular bundles antigos).

Importante: estes documentos são **internos/preparatórios**. Não são “fatura fiscal oficial/certificada”, nem integram com AGT/SAF-T.

Nota: notas internas de crédito/débito **não equivalem** a documentos corretivos oficiais certificados. A preparação para camada oficial fica no Prompt 45.

## Escopo implementado
- Entidade `FiscalAdjustmentAssessment` para governar a decisão fiscal do ajuste operacional.
- Política por tenant (MVP) para decidir comportamento default:
  - `TenantFiscalCorrectionPolicy` (resolução de policy ativa ou default).
- Extensão de `FiscalDocument` para suportar correções internas, referenciando o documento original.
- Endpoints tenant/admin para:
  - listar/detalhar assessments,
  - marcar “sem impacto”,
  - exigir nota de crédito/débito,
  - emitir nota de crédito/débito interna.
- Integração via evento AFTER_COMMIT no fluxo de aprovação do `CaixaOperadorAdjustment` (não reverte ajuste/pagamento em falhas fiscais).
- `taxEvidence` passa a incluir:
  - contadores de assessments,
  - lista de assessments,
  - lista de documentos corretivos internos,
  - warnings relevantes,
  - hashes determinísticos (assessment/documento).

## Não escopo (mantido)
- Não edita `FiscalDocument` original (`ISSUED`) nem “re-emite” documentos antigos.
- Não implementa nota oficial/certificada, QR fiscal oficial, assinatura fiscal oficial, SAF‑T, integração AGT.
- Não implementa emissão fiscal offline.
- Não implementa correção por “linhas manuais” (MVP fica em `SINGLE_ADJUSTMENT_LINE`).
- Não recalcula Evidence Bundles antigos.
- Não altera AppyPay callback/polling.
- Não altera confirmação de pagamento.

## Modelo de domínio

### FiscalAdjustmentAssessment
Registra a avaliação fiscal de um ajuste operacional aprovado.

Campos relevantes:
- `tenant`, `turnoOperacional`, `unidadeAtendimento`
- `adjustment` (`CaixaOperadorAdjustment`) e opcional `divergence` / `caixaOperadorSession`
- `originalFiscalDocument`
- `status` (`FiscalAdjustmentAssessmentStatus`)
- `impactType` (`FiscalAdjustmentImpactType`)
- `decisionReason` (texto sanitizado/curto)
- `assessedBy`, `assessedAt`

Estados (MVP):
- `PENDING`
- `NO_FISCAL_IMPACT`
- `REQUIRES_CREDIT_NOTE`
- `REQUIRES_DEBIT_NOTE`
- `CORRECTION_ISSUED`
- `REJECTED`
- `CANCELLED`

### FiscalDocument (corretivo interno)
`FiscalDocumentType` inclui:
- `INTERNAL_CREDIT_NOTE`
- `INTERNAL_DEBIT_NOTE`

Para correções internas, o documento:
- referencia o original: `originalFiscalDocument`
- aponta para `caixaOperadorAdjustment` e `fiscalAdjustmentAssessment`
- armazena metadados de correção:
  - `correctionSource`
  - `correctionReason`

## Regras de decisão (MVP)
O assessment é criado (quando habilitado) na aprovação do ajuste e começa com heurística simples:
- `NO_LEDGER_IMPACT` → default `NO_TAX_IMPACT` (`NO_FISCAL_IMPACT`)
- `METHOD_RECLASSIFICATION` → default `NO_TAX_IMPACT`
- `ADMIN_CORRECTION`:
  - `DECREASE_*` → tende a `REQUIRES_CREDIT_NOTE`
  - `INCREASE_*` → tende a `REQUIRES_DEBIT_NOTE`

Se não existir `FiscalDocument` original relacionável:
- o assessment pode ficar `PENDING` e o `taxEvidence` gera warning.

## Emissão de correção (MVP)
Modo implementado:
- `FiscalCorrectionLineMode.SINGLE_ADJUSTMENT_LINE`

Interpretação do request `amount` (MVP):
- `amount` é tratado como **gross (total)** da correção; o serviço deriva net/tax respeitando a lógica do Prompt 43.

Restrições:
- se o documento original tiver **múltiplas taxas** → falha (exige linhas manuais, não implementado no MVP).
- não permite emitir correção se o assessment não exige o tipo correspondente.
- não duplica documento corretivo para o mesmo assessment (idempotência por validação/repositório).

## Endpoints (tenant/admin)
Base: `TenantFiscalCorrectionsController`

- `GET /tenant/fiscal/adjustment-assessments`
- `GET /tenant/fiscal/adjustment-assessments/{assessmentId}`
- `POST /tenant/fiscal/adjustment-assessments/{assessmentId}/mark-no-impact`
- `POST /tenant/fiscal/adjustment-assessments/{assessmentId}/require-credit-note`
- `POST /tenant/fiscal/adjustment-assessments/{assessmentId}/require-debit-note`
- `POST /tenant/fiscal/adjustment-assessments/{assessmentId}/issue-credit-note`
- `POST /tenant/fiscal/adjustment-assessments/{assessmentId}/issue-debit-note`

Roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`.

## Integração com Caixa (Prompt 42.2)
No fluxo de aprovação do `CaixaOperadorAdjustment`:
- publica evento AFTER_COMMIT `CaixaOperadorAdjustmentApprovedForFiscalAssessmentEvent`;
- listener cria `FiscalAdjustmentAssessment` quando o tenant tem fiscalidade habilitada/policy ativa;
- qualquer falha fiscal **não reverte** o ajuste operacional aprovado.

## Integração com Evidence Bundle / taxEvidence
`TaxEvidenceService` passa a:
- separar documentos normais vs corretivos (`INTERNAL_CREDIT_NOTE`/`INTERNAL_DEBIT_NOTE`);
- incluir `assessments` e `correctionDocuments` no `taxEvidence`;
- gerar warnings (ex.: assessment pendente / requer nota mas não foi emitida).

Hashes determinísticos:
- `assessmentHash` muda quando status/impactType/assessedAt/decisionReason mudam (sem expor PII).
- `documentHash` do `FiscalDocument` corretivo inclui também os campos de correção (`originalFiscalDocumentId`, `correctionSource`, `correctionReasonHash`, `assessmentId`/`adjustmentId`) além de linhas/totais.

Bundles antigos continuam legíveis sem estes campos.

## Auditoria (sanitizada)
Eventos adicionados (quando aplicável no fluxo):
- `FISCAL_ADJUSTMENT_ASSESSMENT_CREATED`
- `FISCAL_ADJUSTMENT_ASSESSMENT_MARKED_NO_IMPACT`
- `FISCAL_ADJUSTMENT_ASSESSMENT_REQUIRES_CREDIT_NOTE`
- `FISCAL_ADJUSTMENT_ASSESSMENT_REQUIRES_DEBIT_NOTE`
- `FISCAL_INTERNAL_CREDIT_NOTE_ISSUED`
- `FISCAL_INTERNAL_DEBIT_NOTE_ISSUED`
- `FISCAL_CORRECTION_EVIDENCE_ATTACHED_TO_BUNDLE`

Sem payload sensível de pagamento/cartão/AppyPay/telefone.

## Limitações conhecidas (MVP)
- Correção com múltiplas taxas exige modo por linhas (ainda não implementado).
- `correctionReason` é persistido, mas `hash` usa versão sanitizada/truncada (evitar PII).
- Não há UI; apenas endpoints.
- Policy de correções é resolvida no backend; endpoints de gestão de policy podem ser adicionados em etapa futura.

## Nota — relação com inventário (Prompt 44.1)
Nem toda `INTERNAL_CREDIT_NOTE` implica retorno físico de stock. A decisão e o processamento de devolução/reversão de stock/COGS são governados no Prompt 44.1.

## Nota — relação com inventário (Prompt 44.2)
O Prompt 44.2 reforça a diferenciação por `correctionSource` (ex.: `PRODUCT_RETURN`, `DISCOUNT_AFTER_ISSUE`, `PAYMENT_DUPLICATION`, `PARTIAL_REFUND`) e reforça que refund/nota interna não implicam automaticamente retorno físico de stock.

## Comandos executados
- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=FiscalAdjustmentAssessmentServiceTest test`
- `mvn -q -Dtest=FiscalCorrectionDocumentServiceTest test`
- `mvn -q -Dtest=TaxEvidenceCorrectionTest test`

## Próximos passos recomendados
- Suportar correções com múltiplas taxas:
  - `PROPORTIONAL_BY_ORIGINAL_LINES` e/ou `MANUAL_LINES`.
- Adicionar endpoints de gestão de `TenantFiscalCorrectionPolicy` (se necessário).
- Evoluir para correções fiscais oficiais/certificadas em prompt próprio (AGT/SAF‑T).
