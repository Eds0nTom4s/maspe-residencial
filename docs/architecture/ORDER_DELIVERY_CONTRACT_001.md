# ORDER_DELIVERY_CONTRACT_001 — Contrato de Entrega/Finalização de Pedido

**Branch:** `backend/consuma-demo-freezy-delivery-contract-001`  
**Base:** `backend/payment-order-expiration-001` @ `f4600a5d04d9e11475023045f5b957981e838455`  
**Data:** 2026-07-06  
**Fase:** CONSUMA Demo Freezy V1

---

## 1. Objectivo

Formalizar e proteger o contrato de entrega/finalização de pedido pago no contexto da Demo Freezy V1.

O contrato garante que um pedido só pode ser marcado como entregue/finalizado quando:
- O pagamento está confirmado (StatusFinanceiro = PAGO).
- O actor tem permissão operacional no tenant.
- Os subpedidos estão em estado elegível (PRONTO).
- O pedido não está em estado terminal.

O contrato é exposto como endpoint dedicado e complementa o fluxo existente de confirmação de pagamento.

---

## 2. Endpoint de Entrega/Finalização

```
PATCH /tenant/pedidos/{id}/entregar
Authorization: Bearer <tenant-user-token>
Content-Type: application/json

Roles permitidas: TENANT_OWNER, TENANT_ADMIN, TENANT_OPERATOR, TENANT_CASHIER
```

**Response de sucesso (200):**
```json
{
  "success": true,
  "message": "Pedido finalizado",
  "data": {
    "id": 123,
    "status": "FINALIZADO",
    "statusFinanceiro": "PAGO",
    "allowedActions": [],
    ...
  }
}
```

**Erros possíveis:**
| Código | Condição |
|--------|----------|
| 401 | Token inválido ou ausente |
| 403 | Actor sem papel tenant autorizado |
| 404 | Pedido não pertence ao tenant |
| 409 | Pedido terminal / pagamento não confirmado / MARK_DELIVERED bloqueado / turno fechado |

---

## 3. Estado Final Usado

**`FINALIZADO`** — é o estado canónico de terminal para pedido entregue neste projecto.

- Não foi criado estado novo.
- `StatusPedido.FINALIZADO` é derivado automaticamente quando todos os SubPedidos estão `ENTREGUE`.
- `StatusPedido.isTerminal()` retorna `true` para `FINALIZADO` e `CANCELADO`.

---

## 4. Pré-condições Obrigatórias

| # | Condição | Validação |
|---|----------|-----------|
| 1 | Actor autenticado | `@PreAuthorize("isAuthenticated()")` + `TenantGuard` |
| 2 | Actor pertence ao tenant/unidade do pedido | `loadPedido(ctx, pedidoId)` por tenantId |
| 3 | Pedido existe | `ResourceNotFoundException` se não encontrado |
| 4 | Pedido não está CANCELADO | Verificação via `status.isTerminal()` |
| 5 | Pedido não está FINALIZADO | Verificação via `status.isTerminal()` |
| 6 | StatusFinanceiro = PAGO | Verificação explícita antes de executar |
| 7 | MARK_DELIVERED em allowedActions | `PedidoAllowedActionsService.evaluate()` |
| 8 | Turno obrigatório aberto (se aplicável) | `validarTurnoObrigatorio()` via `OperacaoProperties` |

---

## 5. allowedActions — MARK_DELIVERED

### Quando aparece em `allowedActions`:
- Actor tem permissão de produção (`canMoveProduction = true`).
- Turno está ABERTO ou EM_FECHO (se turno obrigatório).
- Todos os subpedidos estão PRONTO ou ENTREGUE.
- **StatusFinanceiro = PAGO** ← novo requisito contratual.
- Pedido não está em estado terminal (FINALIZADO/CANCELADO).

### Quando NÃO aparece (em `actionReasons`):
| Condição | Mensagem |
|----------|----------|
| Pagamento não confirmado | `"Entrega permitida apenas após pagamento confirmado."` |
| Pedido já FINALIZADO | `"Pedido está em estado terminal."` |
| Subpedidos não elegíveis | `"Pedido não possui subpedidos elegíveis para esta ação."` |
| Turno fechado | `"Abra um turno para executar ações operacionais sobre pedidos."` |
| Actor sem permissão | `"Ator não autorizado para esta ação."` |
| Pedido CRIADO (pré-aceite) | Bloqueado por subpedidos não elegíveis |
| Actor = cliente público | Actor resolve como SYSTEM → sem permissão |

---

## 6. Auditoria

Evento registado: **`PEDIDO_STATUS_CHANGED`** (evento canónico existente)

Via `OperationalEventLogService.logPedidoStatusChanged()` com metadata:
```
command         = MARK_DELIVERED
actor           = resolveOrigem() (TENANT_OPERATOR, TENANT_CASHIER, etc.)
statusAnterior  = EM_ANDAMENTO (ou equivalente)
statusNovo      = FINALIZADO
pedidoOrigem    = resolvePedidoOrigem(pedido)
tenantTemplate  = resolveTemplateCode(pedido)
tenant          = ctx.tenantId()
timestamp       = now() (registado pelo OperationalEventLog)
deliveredSubPedidos = [lista de ids de subpedidos marcados ENTREGUE]
```

Não foi criada auditoria paralela — o mecanismo existente atende.

---

## 7. Relação com Pagamento

| Regra | Implementação |
|-------|---------------|
| Entrega exige PAGO | Verificação explícita em `entregarPedido()` |
| Entrega não altera pagamento | `StatusFinanceiro` guardado antes e verificado após |
| Gateway não é chamado | ❌ Não existe chamada ao gateway em `entregarPedido` |
| Estorno não é criado | ❌ Não existe lógica de estorno |
| CONFIRM_PAYMENT não reaparece | Verificado no `PedidoAllowedActionsService` (PAGO → sem CONFIRM_PAYMENT) |

---

## 8. Relação com Acompanhamento Público

- `GET /public/pedidos/{trackingCode}` continua a funcionar sem alteração.
- Após entrega: o status do pedido é `FINALIZADO`.
- A resposta pública reflecte o estado derivado dos SubPedidos.
- O contrato público não foi alterado nesta fase.

---

## 9. O que ficou fora do escopo

| Funcionalidade | Decisão |
|----------------|---------|
| Gateway de pagamento | ❌ Fora de escopo |
| Estorno | ❌ Fora de escopo |
| PDV | ❌ Fora de escopo |
| Caixa/Fecho de Turno | ❌ Fora de escopo |
| Fiscal/Invoice | ❌ Fora de escopo |
| KDS | ❌ Fora de escopo |
| Cancelamento de pedido pago | ❌ Fora de escopo |
| WebSocket/push ao cliente | ❌ Fora de escopo |

---

## 10. Diagrama

```
docs/architecture/diagrams/consuma-demo-freezy-delivery-contract-flow.mmd
```

Ver: [consuma-demo-freezy-delivery-contract-flow.mmd](diagrams/consuma-demo-freezy-delivery-contract-flow.mmd)

---

## 11. Ficheiros Modificados

| Ficheiro | Tipo | Alteração |
|----------|------|-----------|
| `controller/TenantPedidoController.java` | Backend | Novo endpoint PATCH /entregar |
| `service/operacional/PedidoStatusTransitionService.java` | Backend | Novo método `entregarPedido()` |
| `service/operacional/PedidoAllowedActionsService.java` | Backend | MARK_DELIVERED requer PAGO |
| `test/...PedidoEntregaAllowedActionsTest.java` | Test | 10 testes de contrato |

---

*Documento gerado automaticamente pela fase PROMPT-BACKEND-CONSUMA-DEMO-FREEZY-DELIVERY-CONTRACT-001.*
