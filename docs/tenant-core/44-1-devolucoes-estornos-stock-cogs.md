# Prompt 44.1 — Devoluções, estornos e impacto no stock/COGS

## Objetivo
Adicionar governança formal para **devoluções/estornos** com impacto controlado em **stock** e **COGS**, preservando histórico e mantendo o inventário **append-only**.

O sistema passa a suportar:
- registo de devolução total/parcial por pedido/linha;
- reversão de consumo por **movimentos reversos** (sem apagar movimentos antigos);
- políticas de **restock** por linha;
- bloqueio de dupla reversão (não devolver mais do que o consumido menos o já devolvido);
- evidência no `inventoryEvidence` (com `returnHash` determinístico).

## Escopo
- `InventoryReturnRecord` + `InventoryReturnLine` (ciclo DRAFT/SUBMITTED/APPROVED/PROCESSED…)
- processamento de retorno com:
  - `RESTOCK` → cria `InventoryMovement` `RETURN_IN` e incrementa `InventoryItem.currentQuantity`
  - `DO_NOT_RESTOCK`/`WASTE`/`MANUAL_REVIEW` → não altera stock no MVP, gera `warningCode`
- integração opcional com nota interna de crédito (Prompt 43.2) via evento **AFTER_COMMIT** (sem assumir retorno físico automaticamente)
- expansão do `inventoryEvidence` com:
  - contadores e totais de returns
  - lista de returns resumidos
  - `returnHash` determinístico por return

## Não-escopo
- não apaga `InventoryMovement`
- não edita `InventoryConsumptionRecord` original
- não altera `FiscalDocument` original
- não altera confirmação de pagamento / AppyPay callback/polling
- não executa refund financeiro automaticamente
- não recalcula Evidence Bundles antigos
- não implementa UI frontend

## Por que não apagar consumo original
O consumo original representa o histórico de operação. A correção é sempre realizada por **movimentos adicionais** (reversos), garantindo trilha de auditoria e consistência com WORM lógico/evidence.

## Modelo de domínio
### InventoryReturnRecord
Representa a devolução/estorno do ponto de vista do inventário.

Campos-chave:
- `tenant`, `unidadeAtendimento`, `pedido`, `pagamento`
- `consumptionRecord` (origem do consumo a reverter)
- `returnType`, `status`, `source`, `reasonCategory`, `reasonDescription`
- `requestedBy/approvedBy`, `requestedAt/approvedAt/processedAt`
- totais: `totalReturnCost`, `totalRevenueReversed`, `totalTaxReversed`, `totalMarginReversed`

### InventoryReturnLine
Linha da devolução, referenciando a `InventoryConsumptionLine` original.

Campos-chave:
- `consumptionLine`, `inventoryItem`, `recipe`, `pedidoItem`, `product`
- `quantityReturned`, `quantityBaseUnit`
- `unitCost` e `totalCostReversed` **herdados do consumo original**
- `restockPolicy`
- `movement` (quando `RESTOCK`)
- `warningCode` (quando não há restock no MVP)

### Restock policies
- `RESTOCK`: cria movimento `RETURN_IN` e devolve stock
- `DO_NOT_RESTOCK`: não devolve stock (MVP), mantém trilha e custo reverso
- `WASTE`: tratado como não-restock no MVP (mantém trilha)
- `MANUAL_REVIEW`: tratado como não-restock no MVP (mantém trilha)

## Movimentos reversos
- movimentos reversos são **append-only**
- movimento criado no processamento:
  - `InventoryMovementType.RETURN_IN`
  - `InventoryMovementReferenceType.INVENTORY_RETURN`
  - `referenceId = returnRecordId`

## Integração com fiscalidade (Prompt 43.2)
- Evento: `FiscalCreditNoteIssuedForInventoryReturnEvent` (AFTER_COMMIT)
- Listener: `InventoryReturnOnFiscalCreditNoteIssuedListener`
- MVP: cria retorno somente quando `correctionSource=PRODUCT_RETURN` (não cobre partial refund automaticamente)
- Observação: **nem toda nota interna de crédito implica retorno físico de stock**

## Integração com refund (ponto de extensão)
Não há automação de refund neste prompt. O retorno pode ser criado manualmente via endpoints tenant/admin ou solicitado via POS/device.

## Cálculo de COGS reverso
- `unitCost` e `totalCostReversed` são calculados com base na `InventoryConsumptionLine` original (custo histórico)
- não recalcula por `averageCost` atual

## Margem reversa
MVP registra custo reverso e warnings; `totalRevenueReversed/totalTaxReversed/totalMarginReversed` ficam `null` quando não há dados fiscais vinculados.

## Endpoints
### Tenant/Admin (`/tenant/inventory`)
- `GET /returns`
- `GET /returns/{returnId}`
- `GET /returns/{returnId}/lines`
- `POST /returns`
- `POST /returns/{returnId}/submit`
- `POST /returns/{returnId}/approve`
- `POST /returns/{returnId}/reject`
- `POST /returns/{returnId}/process`

### Device/POS (`/device/inventory`)
- `POST /returns` (solicita devolução; não aprova/processa)
- `GET /returns/{returnId}`

## Evidence Bundle (`inventoryEvidence`)
`inventoryEvidence` inclui:
- `totalReturns`, `processedReturns`, `pendingReturns`
- `totalReturnCost`, `totalRevenueReversed`, `totalTaxReversed`, `totalMarginReversed`
- `returnWarnings`
- `returnItems` com `returnHash`

### Hash determinístico
`returnHash` muda se algum campo relevante mudar:
- status/type/source/totais/processedAt
- linhas (qtyBase, unitCost, totalCostReversed, restockPolicy, movementId)

## Auditoria
Eventos adicionados:
- `INVENTORY_RETURN_CREATED`
- `INVENTORY_RETURN_SUBMITTED`
- `INVENTORY_RETURN_APPROVED`
- `INVENTORY_RETURN_REJECTED`
- `INVENTORY_RETURN_PROCESSED`
- `INVENTORY_RETURN_FAILED` (reservado)
- `INVENTORY_RETURN_LINKED_TO_FISCAL_CREDIT_NOTE` (reservado)
- `INVENTORY_RETURN_LINKED_TO_PAYMENT_REFUND` (reservado)
- `INVENTORY_RETURN_EVIDENCE_ATTACHED_TO_BUNDLE` (reservado)

## Erros de domínio (principais)
- `INVENTORY_RETURN_NOT_FOUND`
- `INVENTORY_RETURN_INVALID_STATE`
- `INVENTORY_RETURN_FORBIDDEN`
- `INVENTORY_RETURN_POLICY_DISABLED`
- `INVENTORY_RETURN_CONSUMPTION_NOT_FOUND`
- `INVENTORY_RETURN_EXCEEDS_CONSUMED_QUANTITY`
- `INVENTORY_RETURN_ALREADY_PROCESSED`
- `INVENTORY_RETURN_RESTOCK_POLICY_REQUIRED`
- `INVENTORY_RETURN_PROCESSING_FAILED`

## Limitações do MVP
- `WASTE`/`MANUAL_REVIEW` ainda não criam movimentos específicos; ficam como warning (sem alteração de stock)
- não há endpoints para editar linhas após criação (criar novo return é o fluxo recomendado)
- integração com crédito fiscal é limitada a `correctionSource=PRODUCT_RETURN`
- não calcula automaticamente `totalRevenueReversed/totalTaxReversed/totalMarginReversed`

## Comandos executados
Ver `docs/tenant-core/44-1-relatorio-executivo-devolucoes-estornos-stock-cogs.txt`.

## Checklist de aceitação (resumo)
- entidades/tabelas de return existem
- reversão não edita/apaga consumo ou movimentos anteriores
- restock funciona (`RETURN_IN`)
- dupla reversão é bloqueada
- `inventoryEvidence` inclui returns e `returnHash` determinístico

