# PAYMENT_AFTER_ACCEPTANCE_POLICY_001

## Regra central

Pedidos públicos ou de mesa não devem criar, exibir ou confirmar pagamento definitivo antes do aceite operacional do estabelecimento.

Antes do aceite, o pedido fica enviado/criado e o financeiro permanece `NAO_PAGO`. Depois do aceite, o pedido pode iniciar cobrança, criar ordem manual ou receber confirmação, conforme o método habilitado.

## Matriz de elegibilidade

| Template | Origem | Precisa aceite antes de pagar | Estado mínimo para pagamento | Quem inicia | Quem confirma | Cliente vê instrução antes do aceite | Produção depende de pagamento | Observação |
|---|---|---:|---|---|---|---:|---:|---|
| CONSUMA_REST_V1 | Cardápio público / QR principal / QR mesa | Sim | `EM_ANDAMENTO` | Cliente após aceite ou caixa | Gateway/caixa | Não | Não nesta fase | Pedido nasce `NAO_PAGO`; aceite não confirma pagamento. |
| QR_MESA | QR público de mesa | Sim | `EM_ANDAMENTO` | Cliente após aceite ou caixa | Gateway/caixa | Não | Não nesta fase | Rejeição só é simples enquanto `NAO_PAGO`. |
| QR_PRINCIPAL | QR público sem mesa | Sim | `EM_ANDAMENTO` | Cliente após aceite ou caixa | Gateway/caixa | Não | Não nesta fase | Mesma regra do cardápio público. |
| CONSUMA_PONTO_V1 | POS/device operado | Não para venda operada | Turno aberto e contrato do device | Operador | Device/caixa | Não aplicável | Não nesta fase | Diferenciado por `DEVICE_POS`; não representa cliente pagando antes do aceite. |
| PDV_INTERNO | Operação interna | Não para venda operada | Turno aberto e contrato interno | Operador | Caixa/device | Não aplicável | Não nesta fase | Fluxo interno segue contrato próprio. |
| CAIXA | Pagamento presencial | Sim para pedido público | `EM_ANDAMENTO` | Caixa ou cliente com ordem | Caixa/device | Não | Não nesta fase | Confirmação manual também é bloqueada se pedido ainda está `CRIADO`. |

## Quando pagamento é permitido

- Pedido público/QR: a partir de `EM_ANDAMENTO` ou `FINALIZADO`.
- Pedido device/POS: permitido conforme política do device, método e turno aberto.
- Pedido já `PAGO`: não permite nova cobrança simples.

## Quando pagamento é bloqueado

- Pedido público/QR em `CRIADO`.
- Pedido `CANCELADO`.
- Confirmação manual ou callback de gateway em pedido público ainda `CRIADO`.
- Nova cobrança gateway quando já existe pagamento `PENDENTE`.

## Pedido público vs PDV interno

Pedido público é uma solicitação do cliente e depende de aceite operacional antes de cobrança. PDV/device é uma venda conduzida pelo operador, por isso pode iniciar pagamento imediato quando o contrato do device permitir.

## metodoPagamento público

O payload público legado pode conter `metodoPagamento`, mas o backend não o usa para criar cobrança. Nesta fase ele é tratado como preferência sem efeito financeiro; a UI deixa de enviá-lo no pedido público.

## Pendências

- Gateway real completo.
- Estorno automático.
- Confirmação manual segura com contrato público final.
- Caixa por turno com política completa.
- Conciliação financeira e fiscal de ponta a ponta.
