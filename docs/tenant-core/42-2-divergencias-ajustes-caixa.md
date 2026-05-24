# Prompt 42.2 — Divergências, ajustes formais e justificativas financeiras (Caixa Operador/Device)

## Objetivo
Adicionar governança formal para divergências de caixa (CASH/TPA) detectadas no fecho do `CaixaOperadorSession`, garantindo:
- caixa fechado não é editado informalmente;
- divergências e ajustes existem como registros paralelos, auditáveis;
- fluxo DRAFT → SUBMITTED → APPROVED/REJECTED;
- evidência futura (Evidence Bundle) inclui histórico de divergências/ajustes;
- compatibilidade com bundles antigos e preservação WORM.

## Princípio central
`CaixaOperadorSession` representa o fecho operacional.  
Se houver diferença, o sistema cria uma `CaixaOperadorDivergence` (lado a lado), e opcionalmente um `CaixaOperadorAdjustment` quando aprovado.

## Modelo de dados
Migration:
- `src/main/resources/db/migration/V47__caixa_operador_divergencias_ajustes.sql`

Tabelas:
- `caixa_operador_divergences`
- `caixa_operador_adjustments`

Regras importantes:
- índice unique parcial evita múltiplas divergências abertas por `(tenant, caixa, payment_method, type)` em `DRAFT/SUBMITTED`;
- `absolute_difference_amount >= 0`;
- ajuste (`caixa_operador_adjustments`) não altera valores do caixa.

## Entidades e enums
Entidades:
- `src/main/java/com/restaurante/model/entity/CaixaOperadorDivergence.java`
- `src/main/java/com/restaurante/model/entity/CaixaOperadorAdjustment.java`

Enums:
- `CaixaOperadorDivergenceStatus` (`DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `SUPERSEDED`)
- `CaixaOperadorDivergenceType` (`CASH_SHORTAGE`, `CASH_SURPLUS`, `TPA_SHORTAGE`, `TPA_SURPLUS`, `MIXED_DIFFERENCE`, ...)
- `CaixaOperadorDivergenceSeverity`
- `CaixaOperadorDivergenceReasonCategory`
- `CaixaOperadorAdjustmentType`, `CaixaOperadorAdjustmentDirection`, `CaixaOperadorAdjustmentStatus`

## Fluxo (MVP)
### 1) Detecção automática no fecho do caixa
No `CaixaOperadorSessionService.fechar(...)`, após fechar o caixa:
- se houver diferença CASH/TPA, cria divergências `DRAFT`;
- cria também `MIXED_DIFFERENCE` quando CASH e TPA diferem simultaneamente;
- marca o caixa como `DISPUTED` quando aplicável.

Importante:
- os valores do caixa permanecem como foram fechados;
- divergência/ajuste é trilha formal para governança.

### 2) Justificativa e submissão (device/POS)
Device justifica e submete divergência:
- `POST /device/caixa-operador/divergences/{divergenceId}/justify`
- `POST /device/caixa-operador/divergences/{divergenceId}/submit`

### 3) Revisão (tenant/admin/finance)
Aprovar ou rejeitar:
- `POST /tenant/caixa-operador/divergences/{divergenceId}/approve`
- `POST /tenant/caixa-operador/divergences/{divergenceId}/reject`

Se aprovado com `adjustmentType`, cria `CaixaOperadorAdjustment` (status `APPROVED`).

## Capabilities (device)
Novas capabilities:
- `VIEW_OPERATOR_CASH_DIVERGENCE`
- `JUSTIFY_OPERATOR_CASH_DIVERGENCE`
- `SUBMIT_OPERATOR_CASH_DIVERGENCE`
- `REVIEW_OPERATOR_CASH_DIVERGENCE`
- `VIEW_OPERATOR_CASH_ADJUSTMENT`

Defaults MVP:
- `POS_CAIXA`: view/justify/submit.
- `POS_ATENDIMENTO`: view.

## Auditoria
Eventos adicionados (sanitizados):
- `CAIXA_OPERADOR_DIVERGENCE_CREATED`
- `CAIXA_OPERADOR_DIVERGENCE_JUSTIFIED`
- `CAIXA_OPERADOR_DIVERGENCE_SUBMITTED`
- `CAIXA_OPERADOR_DIVERGENCE_APPROVED`
- `CAIXA_OPERADOR_DIVERGENCE_REJECTED`
- `CAIXA_OPERADOR_ADJUSTMENT_CREATED`
- `CAIXA_OPERADOR_DIVERGENCE_EVIDENCE_ATTACHED_TO_BUNDLE`

Não audita payload sensível nem dados de cartão/telefone.

## Evidence Bundle (integração)
O Evidence Bundle passa a incluir:
- `operatorCashDivergenceEvidence` (seção de divergências/ajustes)

E também enriquece `operatorCashEvidence.sessions` com contadores e flags:
- divergências (total e pendentes)
- ajustes aprovados (count/total)

Hashes determinísticos:
- `divergenceHash` muda quando status/valores/categoria/notes mudam (sem expor texto livre).
- `adjustmentHash` muda quando amount/type/direction/status mudam.

## Limitações
- Não implementa anexos binários (apenas `evidenceReference` textual).
- Não recalcula bundles antigos.
- Não implementa UI.

## Próximo passo recomendado
**Prompt 42.3**: anexos/comprovativos com hash, storage seguro e cadeia de custódia documental.

