# ORDER_ALLOWED_ACTIONS_CONTRACT_001

## Objetivo

Expor, por pedido tenant, quais acoes operacionais o ator autenticado pode executar no estado atual do pedido.

O backend e a autoridade para transicoes. O frontend apenas renderiza acoes a partir de `allowedActions` e nunca deve liberar comando destrutivo por inferencia local.

## Por que a UI nao deve deduzir transicoes

Uma acao de pedido depende de mais do que `statusOperacional`:

- estado financeiro;
- status dos subpedidos;
- template operacional;
- origem do pedido;
- turno aberto;
- ator autenticado;
- role tenant;
- escopo de tenant, instituicao e unidade.

Manter esta regra no frontend cria divergencia entre visual e comando real. A UI pode aplicar protecoes visuais, mas a decisao permissiva precisa vir do backend.

## Campo `allowedActions`

Responses tenant de pedido passam a retornar:

```json
{
  "allowedActions": ["ACCEPT_ORDER", "REJECT_ORDER"],
  "actionReasons": {
    "CONFIRM_PAYMENT": "Pagamento disponivel apenas apos aceite do pedido."
  }
}
```

`allowedActions` e uma lista de strings estaveis. `actionReasons` e opcional e informa motivo simples quando uma acao conhecida esta bloqueada.

## Actions atuais

- `ACCEPT_ORDER`: aceitar pedido operacionalmente.
- `REJECT_ORDER`: rejeitar pedido ainda nao aceite.
- `CANCEL_ORDER`: cancelar pedido operacionalmente quando o comando real permite.
- `CONFIRM_PAYMENT`: reservado para confirmacao financeira real; nao e retornado como permitido nesta fase.
- `VIEW_PAYMENT`: visualizar estado financeiro do pedido.
- `VIEW_EXTRACT`: reservado para extrato/caixa.
- `START_PREPARATION`: iniciar preparo quando ha subpedido pendente e ator de producao.
- `MARK_READY`: marcar pronto quando ha subpedido em preparo e ator de producao.
- `MARK_DELIVERED`: marcar entregue quando todos os subpedidos estao prontos/entregues e ator de producao.

## Como o backend calcula

`PedidoAllowedActionsService` avalia:

- `Pedido.status`;
- `Pedido.statusFinanceiro`;
- status dos `SubPedido`;
- turno operacional;
- ator resolvido a partir de `TenantContext.roles`;
- template/origem via `OperationalTemplatePolicy`.

O service evita chamar comandos transacionais para nao causar efeitos colaterais. Ele espelha apenas elegibilidade conservadora e usa as policies existentes para a parte comum.

## Relacao com OperationalTemplatePolicy

`OperationalTemplatePolicy` continua decidindo:

- template normalizado;
- origem do pedido;
- se aceita pagamento antes do aceite;
- se ator pode aceitar/rejeitar;
- se ator pode confirmar pagamento;
- se ator pode mover producao;
- se turno e requerido.

`PedidoAllowedActionsService` consome essa policy e adiciona estado atual do pedido/subpedido.

## Relacao com PedidoPagamentoPolicy

`PedidoPagamentoPolicy` segue como policy de comando financeiro para bloquear inicio/confirmacao de pagamento antes do aceite.

O contrato de capabilities nao libera `CONFIRM_PAYMENT` nesta fase porque a UI de pedidos nao possui contrato backend seguro para confirmacao manual direta. O usuario ainda consegue ver o estado financeiro com `VIEW_PAYMENT`.

## Compatibilidade

Campos novos sao aditivos:

- clientes antigos ignoram `allowedActions`;
- valores sao strings estaveis;
- `actionReasons` pode estar ausente;
- nenhum campo antigo foi removido.

## Fallback frontend

Quando `allowedActions` esta ausente, o frontend normaliza para lista vazia. Isso preserva compatibilidade sem liberar acoes destrutivas por padrao.

Actions como aceitar, rejeitar e confirmar pagamento so podem ser habilitadas quando a lista contem a action correspondente.

## Auditoria dos responses atuais

| Campo | Backend retorna | Frontend usa | Risco | Recomendacao |
|---|---:|---:|---|---|
| `statusOperacional` | Sim | Sim | UI deduzia aceite/rejeicao por status | Manter como exibicao, nao como permissao |
| `statusFinanceiro` | Sim | Sim | UI deduzia pagamento por estado/metodo | Manter como exibicao, nao como permissao |
| `template/origem` | Parcial/indireto | Parcial | Incompleto para decisao local | Backend calcula via policy |
| `turno` | Indireto pelo filtro/contexto | Sim, via store | UI pode divergir do pedido real | Backend tambem bloqueia por action |
| `allowedActions` | Sim, novo | Sim, novo | Nenhum quando ausente vira lista vazia | Usar como fonte permissiva |
| `actionReasons` | Sim, novo opcional | Sim, titulo/nota | Pode nao existir | Fallback visual seguro |

## Proximas actions futuras

- Expor `CONFIRM_PAYMENT` quando houver contrato tenant seguro para pagamento manual.
- Separar actions de producao por subpedido quando KDS completo exigir granularidade.
- Expor `VIEW_EXTRACT` com escopo de turno/caixa.
- Persistir origem do pedido para remover inferencia.
- Incluir capabilities por ator device/POS quando PDV real estiver fechado.
