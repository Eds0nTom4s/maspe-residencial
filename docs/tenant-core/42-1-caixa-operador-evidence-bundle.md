# Prompt 42.1 — Caixa operador/device no Snapshot Financeiro e Evidence Bundle

## Objetivo
Incluir a evidência operacional de **caixa por operador/device** (Prompt 42) dentro do Evidence Bundle do snapshot financeiro do turno (Linha 37), sem quebrar:
- hash SHA-256 e assinatura HMAC do bundle;
- cadeia (chainHash/chainSignature);
- WORM lógico e trigger anti-mutation;
- compatibilidade com bundles antigos (que não tinham essa seção).

## Diferença entre camadas
- `CaixaOperadorSession`: reconciliação física CASH/TPA no nível operacional (operador + device).
- Snapshot financeiro: fotografia consolidada do turno (totais confirmados, integridade, assinatura).
- Evidence Bundle: prova consolidada (payload assinado, persistível em WORM).

## Nova seção no bundle
O `SnapshotFinanceiroEvidenceBundleResponse` passa a incluir:
- `operatorCashEvidence`

Arquivo:
- `src/main/java/com/restaurante/financeiro/snapshot/evidence/dto/SnapshotFinanceiroEvidenceBundleResponse.java`

DTOs:
- `src/main/java/com/restaurante/financeiro/snapshot/evidence/dto/OperatorCashEvidenceSectionDTO.java`
- `src/main/java/com/restaurante/financeiro/snapshot/evidence/dto/OperatorCashSessionEvidenceItemDTO.java`
- `src/main/java/com/restaurante/financeiro/snapshot/evidence/dto/OperatorCashByOperatorSummaryDTO.java`
- `src/main/java/com/restaurante/financeiro/snapshot/evidence/dto/OperatorCashByDeviceSummaryDTO.java`

## Critério de inclusão (MVP)
Como o Evidence Bundle já é gerado por **turno**, a evidência de caixa é construída por:
- `tenantId + turnoId` (e filtra por `unidadeId` quando fornecido).

Caixas `OPEN` não são ignorados: entram na seção com `warning`.

## Cálculos
Os totais consolidados são derivados de somas dos campos do próprio caixa:
- `expectedCashAmount`, `declaredCashAmount`, `cashDifferenceAmount`
- `expectedTpaAmount`, `declaredTpaAmount`, `tpaDifferenceAmount`
- `expectedManualTotalAmount`, `declaredManualTotalAmount`, `manualDifferenceAmount`

Obs:
- Para caixas não fechados, `declared*` pode ser `null`; no consolidado tratamos como zero, mas emitimos warnings.

## Hash determinístico por caixa
Cada `CaixaOperadorSession` incluído recebe um `sessionHash` SHA-256 determinístico.

Regras:
- string canônica com campos relevantes (ids, status, timestamps e valores);
- `BigDecimal` normalizado para scale=2;
- `null` explicitamente representado;
- não inclui notas livres.

Service:
- `src/main/java/com/restaurante/financeiro/caixa/evidence/service/CaixaOperadorEvidenceService.java`

## Auditoria
Eventos adicionados:
- `CAIXA_OPERADOR_EVIDENCE_SECTION_GENERATED`
- `CAIXA_OPERADOR_EVIDENCE_WARNING_DETECTED`
- `CAIXA_OPERADOR_EVIDENCE_ATTACHED_TO_BUNDLE`

Esses eventos são registrados como eventos do turno no ato de gerar o bundle.

## Compatibilidade com bundles antigos
- Bundles antigos continuam legíveis porque a nova seção é apenas um campo adicional no payload.
- A integridade/hmac do bundle já persistido não muda.
- Novos bundles passam a incluir `operatorCashEvidence` naturalmente.

## Limitações
- Não implementa endpoint de preview por período (o modelo atual é por turno).
- Não reprocessa bundles antigos automaticamente.

## Próximo passo recomendado
**Prompt 42.2**: divergências e ajustes formais (justificativas, anexos/comprovativos, aprovação), mantendo trilha auditável.
