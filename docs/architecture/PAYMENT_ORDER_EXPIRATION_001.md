# PAYMENT_ORDER_EXPIRATION_001

## 1. Objetivo

Esta fase implementa o contrato minimo backend para ordem de pagamento expiravel no Demo FREEZY / CONSUMA_PONTO. A ordem nasce somente apos o aceite operacional do pedido publico, fica disponivel para consulta tenant/publica e pode ser confirmada manualmente por ator autorizado dentro do prazo.

## 2. Pedido, financeiro e ordem de pagamento

Os tres conceitos permanecem separados:

- Pedido: controla o ciclo operacional (`CRIADO`, `EM_ANDAMENTO`, finalizacao/entrega).
- Financeiro: controla o estado de pagamento do pedido (`NAO_PAGO`, `PAGO`, `ESTORNADO`, etc.).
- Ordem de pagamento: representa a solicitacao operacional de pagamento, com valor, metodo solicitado, status proprio, `createdAt` e `expiresAt`.

A ordem nao usa `PAGO` como status proprio e a confirmacao financeira nao finaliza nem entrega o pedido.

## 3. Quando a ordem nasce

A ordem de pagamento do tipo `PEDIDO` e criada em `PedidoStatusTransitionService.aceitarPedido`, depois que o aceite passa pelas policies existentes e o pedido fica `EM_ANDAMENTO`. A criacao usa `OrdemPagamentoService.garantirOrdemPagamentoPedidoAposAceite`.

## 4. Por que a ordem nao nasce antes do aceite

O pedido publico nasce `CRIADO / NAO_PAGO` e ainda nao foi aceito pelo operador. Antes do aceite, o backend nao cria ordem ativa para evitar cobranca de pedido que pode ser rejeitado, cancelado ou ainda nao assumido operacionalmente.

## 5. Estados da ordem

A tabela existente `ordens_pagamento` e o enum existente `OrdemPagamentoStatus` foram reutilizados:

- `AGUARDANDO_CONFIRMACAO`
- `CONFIRMADA`
- `EXPIRADA`
- `CANCELADA`

Para esta fase, a expiracao da ordem criada apos aceite e derivada por leitura/comando, nao persistida automaticamente.

## 6. Expiracao

A expiracao usa `expiresAt = now + consuma.payment.order.expiration-minutes`. Se `now > expiresAt`, a leitura tenant/publica apresenta `EXPIRADA` de forma derivada e a confirmacao manual e bloqueada.

Nao ha scheduler nesta fase. A linha pode continuar fisicamente como `AGUARDANDO_CONFIRMACAO`, mas qualquer comando relevante trata a ordem como expirada.

## 7. Confirmacao manual

O endpoint tenant e:

`PATCH /tenant/pedidos/{id}/payment-order/confirm`

O comando:

- exige tenant e usuario autenticado;
- consulta permissao pela policy operacional existente;
- exige pedido aceito e ainda nao pago;
- exige ordem `AGUARDANDO_CONFIRMACAO` e nao expirada;
- aceita somente `TPA` nesta fase;
- valida valor da ordem contra total do pedido;
- marca a ordem como `CONFIRMADA`;
- cria registro financeiro confirmado sem gateway;
- marca o financeiro do pedido como `PAGO`;
- nao finaliza, entrega, imprime ou gera fiscal.

## 8. allowedActions

`PedidoAllowedActionsService` continua sendo a autoridade para a UI. `VIEW_PAYMENT` foi preservada para atores tenant. `CONFIRM_PAYMENT` aparece somente quando:

- o ator tem permissao pelo template/origem;
- o pedido esta aceito;
- o financeiro ainda nao esta pago/estornado;
- existe ordem visivel;
- a ordem esta `AGUARDANDO_CONFIRMACAO`;
- a ordem nao esta expirada.

Cliente publico nao recebe action interna de confirmacao.

## 9. Auditoria

A implementacao usa `OperationalEventLogService`, sem auditoria paralela. Eventos registrados:

- `ORDEM_PAGAMENTO_CRIADA` na criacao apos aceite;
- `ORDEM_PAGAMENTO_CONFIRMADA_MANUAL` na confirmacao manual;
- `TRANSITION_BLOCKED` quando uma tentativa de confirmacao encontra a ordem expirada.

Os eventos incluem, quando disponivel, comando, pedido, ordem, tenant, template, origem, status anterior/novo e ator.

## 10. Configuracao de tempo

A configuracao geral e:

`consuma.payment.order.expiration-minutes=${CONSUMA_PAYMENT_ORDER_EXPIRATION_MINUTES:10}`

No sandbox, o override aprovado para FREEZY permanece:

`consuma.demo.freezy.payment-order-expiration-minutes=${CONSUMA_FREEZY_PAYMENT_ORDER_EXPIRATION_MINUTES:10}`

E o tempo da ordem usa:

`consuma.payment.order.expiration-minutes=${CONSUMA_FREEZY_PAYMENT_ORDER_EXPIRATION_MINUTES:${CONSUMA_PAYMENT_ORDER_EXPIRATION_MINUTES:10}}`

O service nao hardcoda 10 minutos e usa `Clock` injetavel.

## 11. Fora do escopo

- Gateway real.
- Estorno.
- Caixa completo.
- PDV.
- Cash + troco.
- Impressao de invoice.
- Factura fiscal.
- KDS.
- UI frontend.
- Alteracao de public-menu/OTP.
- Alteracao de `PedidoOrigem`.
- Alteracao de `OperationalTemplatePolicy`.

## 12. Limitacoes conhecidas

- Expiracao e derivada; nao ha job para persistir `EXPIRADA`.
- A confirmacao tenant desta fase e TPA-only.
- Ator com role apenas `TENANT_OPERATOR` continua limitado pela policy existente; atores owner/admin/finance/cashier seguem autorizaveis conforme template.
- A UI ainda nao consome o endpoint novo nesta fase.
- A linha da ordem pode permanecer fisicamente `AGUARDANDO_CONFIRMACAO` apos expirar, embora DTOs e comandos mostrem/bloqueiem como `EXPIRADA`.

## 13. Proximas fases

- UI para exibicao de `paymentOrder` no operador e no acompanhamento publico.
- Mensagens publicas mais ricas de instrucao de pagamento.
- Decidir se a expiracao deve ser persistida por job.
- Ampliar metodos de pagamento se o produto exigir cash/troco ou outros meios.
- Integracoes reais de caixa/fiscal/gateway somente em fases proprias.
