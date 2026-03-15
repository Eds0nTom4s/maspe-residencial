# Questões Abertas para a Equipe de Backend (Checkout Integration)

Olá equipa de Backend! 👋

Durante o mapeamento e testes do fluxo público de pedidos e integração do Checkout via QR Code, levantei algumas questões e pontos de atenção que precisamos de alinhar para garantir que o frontend não encontre bloqueios e não haja ambiguidades na regra de negócio.

Abaixo seguem os pontos que carecem da vossa validação ou de pequenos ajustes na API:

## 1. Tratamento de Saldo Insuficiente no Pedido `PRE_PAGO`
A rota `POST /api/pedidos/cliente` funciona perfeitamente assumindo o fluxo pré-pago e debitando saldo automaticamente, mas quando o saldo é **insuficiente** rececionamos um genérico `400 BAD REQUEST`.
* **Questão:** Podem validar (ou normalizar) se a mensagem devolvida dentro de `ApiResponse.message` no erro de saldo vai ter uma `error code` ou `type` explícito fixo (ex: `INSUFFICIENT_FUNDS`) em vez de depender apenas do texto, para que o Frontend possa invocar o modal de "Recarregar Saldo" dinamicamente de forma segura?

## 2. Abastecimento / Recarga de Fundo via App do Cliente
A documentação atual foca na API de `/api/fundos/{token}/recarregar` cuja anotação `@PreAuthorize` restringe a operação a `GERENTE` e `ADMIN`.
* **Questão:** Para cobrir o self-service total (QR Ordering flow), vai existir um Webhook/Gateway dedicado onde o próprio cliente autenticado pode recarregar a sua carteira utilizando meios bancários tradicionais (AppyPay, Cartões, Referência Multicaixa)? Já existe roteiro para isso?

## 3. Comportamento no Cancelamento de Pedidos e Parcialidade
Identifiquei que `estornarPedido` recompõe o Saldo no `FundoConsumo`. No entanto, os itens num Pedido ganham vida própria e são divididos em `SubPedido` e `ItemPedido` para distribuição por Cozinhas.
* **Questão:** Se o garçom cancelar um Prato em específico mas não a Bebida (mesmo pedido físico), há suporte ao Cancelamento/Estorno Parcial, ou a vossa política atual dita que só o Pedido Pai na sua totalidade é elegível a cancelamento? Como é que o frontend deve representar itens rejeitados individualmente?

## 4. Estado Inicial do POS_PAGO (`NAO_PAGO`)
Quando os pedidos são passados individualmente de `CRIADO` para as cozinhas, eles nascem como `NAO_PAGO` sob o Tipo de Sessão `POS_PAGO`.
* **Questão:** O motor de Pagamentos `/api/sessoes-consumo/{id}/pos-pago/liquidar` efetua o Batch checkout corretamente, mas como o `metodoPagamento` é fixo por essa query, isso impede transações mistas (ex: O cliente quer pagar metade em Numerário e Metade no TPA na mesma conta). Haverá futuramente endpoints para processar pagamentos parciais no POS_PAGO ou vai manter-se atómico por Sessão?

## 5. WebSockets – Eventos de Refresh
De momento o payload desenhado de socket reage primariamente a estado operacional (Cozinha).
* **Questão:** Poderiam certificar-se de ter um tópico que dê broadcast direto de `{ "evento": "fundo_atualizado", "novoSaldo": 2000 }` associado ao `qrCodeSessao`? Sem ele, o frontend do cliente terá que fazer Polling a cada recarga para atualizar o badge de Kz na UI. 

Agradeço se puderem revisar estes pontos. O documento principal das rotas (`checkout_integration_spec.md`) já repousa na branch `main`. Bom trabalho! 🚀
