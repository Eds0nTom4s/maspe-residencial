# OPERATIONAL_TEMPLATE_POLICY_001

## Objetivo

Centralizar as decisoes operacionais por template/origem para que aceite, rejeicao, pagamento, producao, KDS, caixa e turno tenham uma matriz unica e testavel.

Esta fase nao redesenha a maquina de estados, nao implementa PDV real, KDS completo, gateway real, estorno ou documentos fiscais. Ela consolida as decisoes que ja existem nos comandos aprovados.

## Templates conhecidos

| Template | Estado no sistema | Observacao |
|---|---|---|
| CONSUMA_REST_V1 | Persistido como `CONSUMA_REST` + versao 1 ou recebido como `CONSUMA_REST_V1` | Restaurante/cardapio publico/QR. |
| CONSUMA_PONTO_V1 | Persistido como `CONSUMA_PONTO` + versao 1 ou recebido como `CONSUMA_PONTO_V1` | Ponto/operacao curta, com POS/device como diferenciador seguro. |
| QR_MESA | Origem inferida por `Pedido.sessaoConsumo.mesa != null` | Pedido publico associado a mesa/local. |
| QR_PRINCIPAL | Origem inferida por sessao sem mesa e com QR de sessao | Pedido publico sem mesa. |
| PDV_INTERNO | Conceitual nesta fase | Venda operada autenticada; contrato real futuro. |
| KDS | Ator/vertical operacional | Nao confirma pagamento. Move apenas producao. |
| CAIXA | Ator/vertical financeira | Confirma pagamento em fluxo financeiro real. Nao move producao sem comando explicito. |

## Origem dos pedidos

Hoje a origem do pedido nao e uma coluna propria. A policy resolve a origem a partir do contexto:

- `DEVICE_POS`: ator device POS autenticado.
- `KDS`: ator device KDS.
- `SESSAO_PARTICIPANTE`: pedido ligado a participante.
- `QR_MESA`: pedido com sessao de consumo e mesa.
- `QR_PRINCIPAL`: pedido com sessao de consumo sem mesa.
- `DIRETO_OU_LEGADO`: pedido sem sessao de consumo.

## Matriz operacional

| Template | Origem tipica | Ator principal | Exige turno | Exige aceite | Pagamento antes do aceite | Pagamento imediato | Producao/KDS | Quem aceita | Quem rejeita | Quem confirma pagamento | Observacoes |
|---|---|---|---:|---:|---:|---:|---|---|---|---|---|
| CONSUMA_REST_V1 | QR principal/QR mesa/cardapio publico | Operador tenant | Sim | Sim para publico | Nao | Nao para publico | Obrigatoria/aplicavel | Owner/Admin/Operator/Cashier | Owner/Admin/Operator/Cashier | Caixa/Finance/Gateway | Pedido nasce `NAO_PAGO`; aceite nao confirma pagamento. |
| QR_MESA | Sessao com mesa | Operador tenant | Sim | Sim | Nao | Nao | Obrigatoria/aplicavel | Owner/Admin/Operator/Cashier | Owner/Admin/Operator/Cashier | Caixa/Finance/Gateway | Entrega a mesa; KDS conforme configuracao. |
| QR_PRINCIPAL | Sessao sem mesa | Operador tenant | Sim | Sim | Nao | Nao | Obrigatoria/aplicavel | Owner/Admin/Operator/Cashier | Owner/Admin/Operator/Cashier | Caixa/Finance/Gateway | Retirada/takeaway/delivery ficam para contrato futuro. |
| CONSUMA_PONTO_V1 | Device POS ou QR simples | Operador autenticado | Sim | Publico sim; device pode dispensar | Nao para publico | Sim apenas em `DEVICE_POS`/operador aplicavel | Opcional | Owner/Admin/Operator/Cashier quando houver pedido publico | Owner/Admin/Operator/Cashier quando houver pedido publico | Caixa/Finance/Device POS | Nao libera pagamento falso; fluxo curto real fica preso ao ator autenticado. |
| PDV_INTERNO | Venda interna futura | Operador autenticado | Sim | Nao para venda operada | Nao aplicavel | Possivel quando contrato real existir | Opcional | Nao aplicavel ao pedido publico | Nao aplicavel ao pedido publico | Caixa/Device POS futuro | Conceitual nesta fase. |
| KDS | Producao | Cozinha/KDS | Sim | Nao aceita pedido | Nao | Nao | Obrigatoria para producao | Nao | Nao | Nao | KDS move producao, nao financeiro. |
| CAIXA | Financeiro | Caixa/financeiro | Sim | Nao altera aceite sozinho | Nao para publico antes do aceite | Apenas fluxo financeiro real | Inexistente | Pode operar comando existente quando autorizado | Pode operar comando existente quando autorizado | Sim | Caixa registra metodo e alimenta turno; nao move producao. |

## Atores por template

- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`: podem aceitar/rejeitar pedidos publicos quando o comando existe e a origem permite.
- `TENANT_CASHIER`, `TENANT_FINANCE`, `GATEWAY`, `DEVICE_POS`: podem confirmar pagamento conforme fluxo financeiro real.
- `TENANT_KITCHEN`, `DEVICE_KDS`: podem mover producao; nao confirmam pagamento.
- `QR_PUBLICO`: cria pedido e solicita pagamento publico, mas nao executa comando interno.

## Pagamento por template

- QR mesa, QR principal e pedidos publicos de restaurante exigem aceite antes de iniciar ou confirmar pagamento.
- `CONSUMA_PONTO_V1` preserva regra diferenciada apenas para venda operada por `DEVICE_POS` ou origem interna autenticada.
- `PDV_INTERNO` fica preparado como origem futura, sem liberar fluxo falso nesta fase.
- KDS nunca confirma pagamento.
- Caixa confirma pagamento apenas pelos servicos financeiros existentes.

## Producao/KDS por template

- Restaurante, QR mesa e QR principal possuem producao/KDS aplicavel.
- Ponto e PDV interno possuem producao opcional, dependente de configuracao e contrato operacional.
- Caixa nao altera producao.
- KDS nao altera caixa.

## Caixa por template

Caixa e uma vertical financeira, nao uma origem de pedido publico. Ele pode confirmar pagamento por fluxo financeiro real e alimentar relatorios/extrato de turno. Ele nao deve iniciar producao nem alterar status de subpedido sem comando operacional explicito.

## Turno por template

Turno segue obrigatorio para templates operacionais. A policy expoe `requiresTurno(...)` para evitar que futuras excecoes fiquem espalhadas em services.

## Implementado agora

- Criada `OperationalTemplatePolicy`.
- `PedidoPagamentoPolicy` passou a consultar a policy central para decidir aceite antes de pagamento.
- `PedidoStatusTransitionService` passou a consultar a policy central em aceitar/rejeitar e no guard de turno.
- Testes unitarios cobrem matriz minima, KDS sem pagamento, caixa sem producao, PONTO/device imediato e QR/publico apos aceite.

## Fases futuras

- Persistir origem do pedido como campo proprio.
- Expor capabilities por pedido para frontend.
- Implementar PDV real com contrato financeiro.
- Implementar KDS completo por configuracao de producao.
- Fechar caixa por turno, conciliacao e estorno controlado.
- Amarrar documentos fiscais a eventos financeiros confirmados.

## Recomendacao de allowedActions

Quando o backend expuser capacidades por pedido, o contrato deve ser calculado no backend e consumido pela UI sem deducao local:

- `ACCEPT`
- `REJECT`
- `CANCEL`
- `START_PREPARATION`
- `MARK_READY`
- `DELIVER`
- `CONFIRM_PAYMENT`

Cada item deve vir acompanhado do motivo quando bloqueado, por exemplo `requiresAcceptance`, `requiresOpenTurno`, `paymentPending`, `actorNotAllowed` ou `templateNotAllowed`.
