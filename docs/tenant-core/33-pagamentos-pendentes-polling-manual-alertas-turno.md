# Prompt 33 — Pagamentos pendentes (tenant) + polling manual + alertas por turno

## Objetivo

Dar visibilidade operacional para pagamentos pendentes e o estado do polling automático, além de permitir **polling manual** controlado por RBAC e gerar **alertas financeiros por turno**.

Esta fase **não** altera:
- callback AppyPay
- fluxos de iniciar pagamento (QR/POS)
- cash/manual, refund, settlement, conciliação final

## Endpoints (tenant)

Base: `/tenant/financeiro`

- `GET /pagamentos/pendentes`
- `GET /pagamentos/pendentes/resumo`
- `GET /pagamentos/{pagamentoId}/polling`
- `POST /pagamentos/{pagamentoId}/poll`
- `GET /turnos/{turnoId}/alertas-pagamento`

## RBAC

- Listagem/resumo/detalhe: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE` (+ `TENANT_CASHIER` e `TENANT_OPERATOR` como read-only).
- Polling manual (`POST /poll`): apenas `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`.
- `TENANT_KITCHEN` bloqueado em todos endpoints financeiros.

## Alert level / actionRecommended

Heurística MVP:
- `WARNING` após `consuma.financeiro.pending-payments.warning-after-minutes`
- `CRITICAL` após `consuma.financeiro.pending-payments.critical-after-minutes` ou `pollingStatus=MAX_ATTEMPTS_REACHED`

Recomendação de ação (MVP):
- pendência recente: `WAIT_CALLBACK`
- polling em progresso: `WAIT_NEXT_POLL`
- crítico/erro/max attempts: `MANUAL_POLL`

## Polling manual

- Reusa o mesmo fluxo do polling (consulta AppyPay `getCharge`) e confirma via `PagamentoConfirmacaoService` quando status for `CONFIRMED`.
- Auditoria em `OperationalEventLog`:
  - `PAGAMENTO_POLLING_MANUAL_SOLICITADO`
  - `PAGAMENTO_POLLING_MANUAL_EXECUTADO`

## Alertas por turno

`GET /turnos/{turnoId}/alertas-pagamento` retorna indicadores de pendência no período do turno (via vínculo `Pedido.turnoOperacional`).

Property:
- `consuma.financeiro.pending-payments.block-turno-close-on-critical=false` (MVP não bloqueia fecho automaticamente; apenas alerta).

## Limitações (intencionais)

- MVP não inclui dashboard UI.
- Alertas são heurísticos e podem evoluir para políticas por tenant/plano e regras mais finas.

