# Prompt 43 — Fiscalidade, IVA e obrigações fiscais básicas por tenant

## Objetivo
Implementar uma base fiscal mínima, **configurável e tenant-aware**, para cálculo de IVA, emissão de **documento fiscal interno** (não certificado) e consolidação de evidência fiscal no **Evidence Bundle**.

Este módulo é **preparatório**: não pretende cumprir, nesta fase, requisitos de faturação eletrónica certificada ou integração fiscal oficial.

## Escopo implementado
- **Perfil fiscal do tenant** (`TenantFiscalProfile`) com regime fiscal e flags de emissão.
- **Taxas versionadas** (`TaxRate`) com seed idempotente para Angola (AO).
- **Política fiscal do tenant** (`TenantTaxPolicy`) incluindo `pricesIncludeTax`.
- **Classificação fiscal por produto** (`ProductTaxClassification`).
- **Cálculo de IVA por item/pedido** (`TaxCalculationService`) com `BigDecimal`, scale e rounding explícitos.
- **Documento fiscal interno** (`FiscalDocument` + `FiscalDocumentLine`) com congelamento do cálculo fiscal.
- **Numeração interna sequencial** (`FiscalDocumentSequenceService`) com lock transacional.
- **Endpoints tenant/admin** para configurar e emitir/cancelar documento interno.
- **Endpoints device/POS** para emitir/consultar documento interno via capabilities.
- **taxEvidence** no Evidence Bundle (novos bundles), incluindo `documentHash` determinístico.

## Não escopo (explicitamente não implementado)
- Integração oficial com AGT, SAF-T, faturação eletrónica certificada, QR fiscal oficial, assinatura fiscal oficial.
- Submissão fiscal automática.
- Emissão fiscal offline (reserva de numeração offline).
- Notas de crédito/débito formais e correções fiscais oficiais.
- Reprocessamento/recalculo de Evidence Bundles antigos.

## Documento fiscal interno (importante)
O `FiscalDocument` deste prompt é um **documento fiscal interno/preparatório**.

Ele **não** deve ser descrito como “fatura fiscal oficial” ou “fatura certificada”.

## Cálculo de IVA
O `TaxCalculationService` suporta:
- `pricesIncludeTax=true`:
  - `gross = preço informado`
  - `net = gross / (1 + rate)`
  - `tax = gross - net`
- `pricesIncludeTax=false`:
  - `net = preço informado`
  - `tax = net * rate`
  - `gross = net + tax`

Regras:
- cálculo com `BigDecimal`;
- `consuma.tax.monetary-scale`, `consuma.tax.calculation-scale`, `consuma.tax.rounding-mode`.

## Evidence Bundle / taxEvidence
Novos bundles passam a incluir `taxEvidence`:
- totais (taxable/exempt/tax/gross);
- lista de documentos do período/turno;
- `documentHash` determinístico (inclui linhas).

Bundles antigos continuam legíveis sem `taxEvidence`.

## 43.1 — Automatização fiscal controlada pós-pagamento
Ver: `docs/tenant-core/43-1-automatizacao-fiscal-pos-pagamento.md`.

## 43.2 — Notas de crédito/débito internas e impacto fiscal de ajustes
Ver: `docs/tenant-core/43-2-notas-credito-debito-ajustes-fiscais.md`.

## Dívidas futuras (registradas)
- P43-DEBT-2: emissão fiscal offline controlada (reserva de numeração e reconciliação).
- P43-DEBT-3: correções fiscais oficiais/certificadas (AGT/SAF-T), fora do escopo interno/preparatório.
