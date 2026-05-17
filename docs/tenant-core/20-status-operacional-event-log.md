# Prompt 20 — Status operacional controlado + Operational Event Log (tenant-aware)

## Objetivo
Adicionar rastreabilidade operacional e transições controladas para **Pedido/SubPedido**, sem misturar com financeiro:
- validar transições de status operacionais
- aplicar RBAC por role
- registrar eventos operacionais append-only em `operational_event_logs`
- manter separação: operacional ≠ financeiro

## Separação operacional vs financeiro
- Financeiro (`StatusFinanceiroPedido`): `NAO_PAGO`, `PENDENTE_PAGAMENTO`, `PAGO`, `ESTORNADO`
- Operacional:
  - Pedido (`StatusPedido`): `CRIADO`, `EM_ANDAMENTO`, `FINALIZADO`, `CANCELADO` (derivado dos SubPedidos)
  - SubPedido (`StatusSubPedido`): `CRIADO`, `PENDENTE`, `EM_PREPARACAO`, `PRONTO`, `ENTREGUE`, `CANCELADO`

Regra crítica:
- Mudanças operacionais **não alteram** pagamento, callback, AppyPay, conciliação ou `statusFinanceiro`.

## Operational Event Log
Tabela: `operational_event_logs`
- tenant-aware (`tenant_id` obrigatório)
- referências opcionais: `pedido_id`, `sub_pedido_id`, `mesa_id`, `actor_user_id`, `device_id`
- append-only (não atualizar, não apagar)

Uso:
- registrar alterações de status de SubPedido
- registrar alterações derivadas do Pedido quando recalculadas a partir de SubPedido
- registrar tentativas bloqueadas (transition blocked)

## Endpoints
### SubPedido (produção)
- `PATCH /api/tenant/producao/subpedidos/{id}/status`
  - Roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_KITCHEN`
  - Cozinha (KITCHEN) limitada a: `EM_PREPARACAO` e `PRONTO`
  - `CANCELADO` exige `motivo`

### Pedido (tenant-admin)
- `PATCH /api/tenant/pedidos/{id}/status`
  - Roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`
  - Nota: `Pedido.status` é derivado dos SubPedidos.
  - Nesta fase, a operação explícita suportada é `CANCELADO` (cancela subpedidos quando válido).
  - Pedido `PAGO` não pode ser cancelado operacionalmente nesta fase.

### Eventos operacionais
- `GET /api/tenant/operacional/eventos`
  - Roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`
  - Filtros: `pedidoId`, `subPedidoId`, `eventType`, `actorUserId`, `deviceId`, `de`, `ate`, paginação.

## Actor (USER/DEVICE/SYSTEM)
Nesta fase:
- eventos registram `actor_user_id` quando existe `TenantContext.userId`
- estrutura suporta `device_id` (para fase futura de POS alterando status via deviceToken)

## Códigos HTTP
- 404: recurso não pertence ao tenant (cross-tenant) / não encontrado
- 403: role insuficiente
- 409: transição inválida pelo estado atual
- 400: request inválido (ex.: motivo obrigatório ausente)

## Próximos passos
- Permitir update operacional via dispositivo (POS/KDS) usando `device_id` no event log.
- Métricas/SLA: tempos de preparação, gargalos por unidade de produção, etc.
- Agregação mais rica de estado do Pedido (opcional) se necessário para UX (sem deformar o core).

