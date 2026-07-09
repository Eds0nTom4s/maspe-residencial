# Prompt 37 — Relatório de caixa por turno (CASH/TPA + AppyPay) + resumo operacional financeiro

## Objetivo

Disponibilizar um relatório operacional financeiro por `TurnoOperacional`, consolidando:

- Pagamentos manuais confirmados por POS (`CASH`, `TPA`).
- Pagamentos digitais (AppyPay) confirmados por `callback`/`polling`.
- Separação por destino (`PEDIDO` vs `FUNDO_CONSUMO`), pendências e divergências.

Este relatório **não** é conciliação bancária final, settlement, fiscalidade, nem export PDF/Excel.

## Endpoint

`GET /tenant/financeiro/turnos/{turnoId}/relatorio-caixa`

### RBAC

Permitidos:
- `TENANT_OWNER`
- `TENANT_ADMIN`
- `TENANT_FINANCE`
- `TENANT_CASHIER` (read-only)
- `TENANT_OPERATOR` (read-only)

Bloqueado:
- `TENANT_KITCHEN`

### Regras de segurança

- `tenantId` vem do `TenantContext`.
- Turno fora do tenant → `404`.
- Não expor payload bruto do gateway, tokens, hashes ou secrets.

## Modelo de cálculo (MVP)

### Confirmados manuais

Baseado em `OrdemPagamento` do turno:
- `status=CONFIRMADA`
- `metodoSolicitado=CASH|TPA`
- `tipo=PEDIDO` ou `FUNDO_CONSUMO`

O manual entra como **confirmado imediato**, pois é confirmado pelo operador/POS.

### Confirmados AppyPay (gateway)

Baseado em `PagamentoGateway`/`Pagamento` associado ao turno (via `Pedido.turnoOperacional`) e recargas no intervalo do turno:
- Confirmado apenas quando status local está `CONFIRMADO` (callback/polling).
- Pendente não entra como confirmado.

### Pendências

- Pendências de gateway: pagamentos `PENDENTE` associados ao turno.
- Pendências manuais: ordens `AGUARDANDO_CONFIRMACAO` (quando habilitado por property).

### Divergências

Quando existirem eventos de divergência (ex.: valor/moeda) gerados por callback/polling, o relatório expõe:
- `totalDivergente`
- alertas financeiros existentes do turno
- eventos financeiros recentes sanitizados

## Integração com pré-fecho (resumo reduzido)

O pré-fecho do turno inclui `resumoCaixa` (`ResumoCaixaTurnoMiniResponse`) com:
- total confirmado geral
- total por método (CASH/TPA/APPYPAY)
- total pendente
- total por destino (pagamento de pedidos vs carregamento de fundo)

O detalhe completo permanece apenas no endpoint do relatório.

## Configurações

`application.properties`:

- `consuma.financeiro.caixa-turno.eventos-recentes-limit` (default 20)
- `consuma.financeiro.caixa-turno.incluir-eventos` (default true)
- `consuma.financeiro.caixa-turno.incluir-pendentes` (default true)

## Limitações

- Não substitui conciliação bancária final.
- Não implementa export PDF/Excel.
- Não implementa refund/cancelamento financeiro.
- Não altera callback/polling AppyPay.

## Próximos passos (prováveis)

- Snapshot financeiro no `resumo_json` do turno no momento do fecho (se ainda não persistido).
- Export lógico (JSON assinado) e trilha de auditoria de export.
- Relatório de caixa “auditável” (com trilha completa por ordem/pagamento/evento) para operações maiores.

