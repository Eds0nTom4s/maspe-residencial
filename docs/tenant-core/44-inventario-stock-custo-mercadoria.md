# Prompt 44 — Inventário, Stock e Custo de Mercadoria (COGS)

## Objetivo
Implementar a base operacional de inventário da CONSUMA, permitindo controlar stock (entradas/saídas/perdas/ajustes), receitas técnicas (composição), baixa automática por pedido pago e cálculo estimado de COGS e margem bruta, com auditoria e evidência no Evidence Bundle.

## Escopo (implementado)
- Itens de inventário (`InventoryItem`) com `currentQuantity` materializado e custo médio (`averageCost`).
- Unidades de medida (`UnitOfMeasure`) e conversões (`UnitConversion`) para normalizar quantidades em “base unit”.
- Receitas técnicas (`InventoryRecipe` + `InventoryRecipeLine`) versionáveis por status (MVP: `ACTIVE`/`ARCHIVED`) com `yieldQuantity`.
- Mapeamento produto → política de stock (`ProductInventoryMapping`).
- Movimentos append-only (`InventoryMovement`).
- Consumo automático no evento “pagamento confirmado”:
  - cria `InventoryConsumptionRecord` idempotente por `tenantId+pedidoId`;
  - cria `InventoryMovement` do tipo `SALE_CONSUMPTION` por ingrediente/linha.
- Cálculo de COGS e margem estimada por pedido:
  - `totalCost` (COGS) = soma dos custos das linhas consumidas;
  - `netRevenueAmount` preferencialmente via `FiscalDocument` (quando existir);
  - fallback para `Pedido.total` quando não houver documento fiscal.
- Seção `inventoryEvidence` no Evidence Bundle (novos bundles) com totais e hashes determinísticos por consumo.

## Não escopo (não implementado)
- Contabilidade geral, razão, lançamentos contabilísticos.
- Inventário fiscal oficial (SAF‑T/AGT).
- Emissão de inventário offline definitivo no POS.
- Gestão avançada de compras/fornecedores.
- Múltiplos armazéns complexos, lotes/validade, rastreio sanitário.
- Reversão automática de stock por estornos/devoluções (registrado como dívida futura).

## Modelo de domínio (resumo)
- `UnitOfMeasure`, `UnitConversion`
- `TenantInventoryPolicy`
- `InventoryItem`
- `InventoryRecipe`, `InventoryRecipeLine`
- `ProductInventoryMapping`
- `InventoryMovement` (append-only)
- `InventoryConsumptionRecord`, `InventoryConsumptionLine`

## Movimentos (append-only)
- Entradas: `PURCHASE_IN`
- Consumo de venda: `SALE_CONSUMPTION`
- Perdas: `WASTE`
- Ajustes: `ADJUSTMENT_IN` / `ADJUSTMENT_OUT`

## Entradas / Ajustes / Perdas
- `stock-in`: atualiza `currentQuantity`, `averageCost` (custo médio ponderado) e cria `InventoryMovement`.
- `adjust`: cria `InventoryMovement` de ajuste e atualiza quantidade (respeita `allowNegativeStock`).
- `waste`: cria `InventoryMovement` de perda e atualiza quantidade (respeita `allowNegativeStock`).

## Baixa automática (trigger)
Default (MVP): `PAYMENT_CONFIRMED`.

Integração:
- Listener `AFTER_COMMIT` reage ao `PaymentConfirmedForFiscalIssueEvent` (mesmo evento já usado pelo Prompt 43.1), sem alterar AppyPay/callback/polling.
- Consumo é idempotente por pedido: se já consumido, não repete.

## COGS e margem
- COGS (estimado): custo por ingrediente com base em `averageCost` (MVP).
- Margem (estimada): `netRevenueAmount - totalCost`.
  - `netRevenueAmount` usa dados do documento fiscal interno quando disponível.

## Integrações e separação de responsabilidades
- Caixa: reconcilia recebimento (não calcula stock).
- Fiscalidade: calcula imposto (não calcula custo).
- Inventário: calcula consumo/custo/margem (não altera pagamento).
- Evidence Bundle: consolida `operatorCashEvidence`, `taxEvidence` e agora `inventoryEvidence`.

## Evidence Bundle (`inventoryEvidence`)
Novos bundles incluem `inventoryEvidence` com:
- totais de movimentos por tipo e custo agregado;
- totais de receita/net/tax e COGS;
- `consumptionHash` determinístico por `InventoryConsumptionRecord`.

Bundles antigos continuam legíveis (campo ausente → `null`).

## Auditoria
Eventos (sanitizados) incluídos no `OperationalEventType`:
- `INVENTORY_ITEM_CREATED`, `INVENTORY_ITEM_UPDATED`
- `INVENTORY_STOCK_IN_CREATED`, `INVENTORY_ADJUSTMENT_CREATED`, `INVENTORY_WASTE_CREATED`
- `INVENTORY_CONSUMPTION_REQUESTED`, `INVENTORY_CONSUMPTION_COMPLETED`, `INVENTORY_CONSUMPTION_FAILED`, `INVENTORY_CONSUMPTION_SKIPPED`
- `INVENTORY_EVIDENCE_ATTACHED_TO_BUNDLE` (MVP: evidência gerada no bundle; auditoria pode ser evoluída)

## Limitações (MVP)
- Sem reversão automática de consumo em caso de devolução/estorno.
- Sem bloqueio rígido de venda por stock insuficiente (respeita `allowNegativeStock` e warnings).
- Receitas com múltiplas unidades exigem conversões configuradas.

## Dívidas futuras
- P44-DEBT-1 — Impacto de devoluções/estornos/notas internas sobre stock e COGS.
- P44-DEBT-2 — Inventário offline controlado com reconciliação.

## Comandos
- `mvn -q -DskipTests compile`
- `mvn test`

## Checklist de aceitação (resumo)
- Inventário core e migrations criadas.
- Stock-in / ajuste / waste registram movimentos append-only.
- Consumo automático no pagamento confirmado é idempotente.
- COGS e margem são calculados e evidenciados no Evidence Bundle.

