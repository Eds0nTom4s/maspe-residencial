# CONSUMA Demo Freezy Happy Path 001

Data: 2026-07-07

## Definição

CONSUMA Demo Freezy V1 é a primeira baseline congelada de demonstração da CONSUMA para validar um fluxo funcional, estável e apresentável da vertical `CONSUMA_PONTO_V1`.

Freezy não é marca, tenant ou vertical. Freezy é a nomenclatura interna da demonstração congelada.

O backend ainda pode usar nomenclaturas legadas de domínio, mas a baseline demonstrada é `CONSUMA_PONTO_V1`.

## Vertical e canal

Vertical demonstrada: `CONSUMA_PONTO_V1`.

Canal demonstrado: QR Público / Cardápio Público.

O fluxo validado é:

1. Cliente abre o QR Público.
2. Cliente cria pedido.
3. Pedido nasce `CRIADO` / `NAO_PAGO`.
4. Operador aceita o pedido.
5. Backend gera ordem de pagamento expirável.
6. Cliente acompanha instrução de pagamento.
7. Operador confirma pagamento TPA.
8. Financeiro fica `PAGO`.
9. Operador entrega/finaliza pedido.
10. Cliente vê estado final.

## Atores

- Cliente público: cria e acompanha o pedido via QR Público.
- Operador: aceita pedido, confirma pagamento e entrega quando `allowedActions` permitir.
- Backend: autoridade final de estados, pagamento, ordem de pagamento, subpedidos e ações permitidas.
- Sistema de eventos: regista transições operacionais e financeiras.

## Estados validados

- Pedido operacional: `CRIADO`, `EM_ANDAMENTO`, `FINALIZADO`.
- Pedido financeiro: `NAO_PAGO`, `PAGO`.
- Ordem de pagamento: `AGUARDANDO_CONFIRMACAO`, `CONFIRMADA`.
- Subpedidos: `CRIADO`, `PENDENTE`, `ENTREGUE`.

## Pagamento após aceite

Pedidos públicos do `CONSUMA_PONTO_V1` exigem aceite antes de confirmação de pagamento. Antes do aceite não existe `paymentOrder`. Após o aceite, o backend cria uma ordem de pagamento manual/TPA expirável.

## Ordem expirável

A ordem nasce em `AGUARDANDO_CONFIRMACAO` com `expiresAt` definido. Se expirada, `CONFIRM_PAYMENT` deixa de ser permitido e a confirmação é bloqueada.

## Entrega no CONSUMA_PONTO_V1

No `CONSUMA_PONTO_V1`, `KitchenFlow` é `OPTIONAL`. Após pagamento `PAGO`, `MARK_DELIVERED` pode ser exposto mesmo com subpedidos em `PENDENTE`. No ato de entrega, esses subpedidos são transicionados para `ENTREGUE`, o pedido global deriva `FINALIZADO` e o pagamento permanece `PAGO`.

## Separação REST/KDS

`CONSUMA_REST_V1`, QR de mesa e `DEVICE_KDS` preservam produção obrigatória quando aplicável. A flexibilidade de subpedido `PENDENTE` pertence ao template PONTO; não é uma regra por demo.

## allowedActions

- `CONFIRM_PAYMENT`: aparece após aceite quando há ordem aguardando confirmação, não expirada e actor autorizado.
- `MARK_DELIVERED`: aparece apenas com pagamento `PAGO`, actor autorizado, turno válido e subpedidos elegíveis para o template.

Após a finalização, `MARK_DELIVERED` e `CONFIRM_PAYMENT` desaparecem.

## Fora de escopo

Não foram implementados nesta baseline:

- gateway real;
- GPO real;
- referência bancária real;
- estorno;
- PDV;
- cash/troco;
- caixa;
- fiscal;
- KDS;
- Delivery;
- Service;
- REST demo.

## Critérios para demonstração

- Backend é autoridade final do fluxo.
- Frontend consome `allowedActions` e não decide localmente ações críticas.
- Pedido público passa pelo ciclo completo sem gateway real.
- Público não recebe dados internos de confirmação.
- A demonstração deve usar `CONSUMA_PONTO_V1` e QR Público.

## Riscos restantes

- A baseline valida pagamento manual/TPA operacional, não integração real de adquirente.
- Concorrência simultânea de comandos ainda depende das garantias transacionais existentes.
- A demonstração não cobre fluxos REST, Delivery, Service, caixa ou fiscal.
