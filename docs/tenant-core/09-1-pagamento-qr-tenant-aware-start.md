# Prompt 9.1 — Iniciar pagamento por QR (tenant-aware, sem callback)

Data: 2026-05-16

## Objetivo

Permitir iniciar pagamento digital (AppyPay) para um **Pedido** criado no fluxo público por QR:

- validar QR → Tenant/Instituicao/Unidade/Mesa
- validar Pedido por `tenant_id` e contexto do QR
- criar Pagamento **tenant-aware**
- gerar `externalReference` compacta (<= 15 chars)
- chamar gateway (criação de charge)
- retornar instruções ao cliente

Sem implementar ainda:

- callback/confirmação do pagamento
- conciliação / settlement
- estorno
- POS/dispositivo

## Mudanças principais

### Pagamento tenant-aware

Tabela `pagamentos_gateway` passou a ter:

- `tenant_id NOT NULL` + FK para `tenants(id)`

Backfill:

- preferencialmente via `pagamento.pedido → pedidos.tenant_id`
- fallback via `pagamento.fundo → sessao → instituicao → tenant`
- fallback final: tenant `LEGACY`

### Idempotência no “start payment”

Tabela:

- `public_qr_payment_requests`

Unique:

- `(tenant_id, pedido_id, idempotency_key)`

Regras:

- Header obrigatório `Idempotency-Key`
- retry mesma chave+payload retorna o mesmo pagamento
- mesma chave com payload diferente retorna 409

### externalReference compacta (<=15)

`PaymentReferenceService` gera referência curta tenant-aware, adequada ao AppyPay (merchantTransactionId <= 15).

## Endpoint público

- `POST /api/public/q/{token}/pedidos/{pedidoId}/pagamentos`

Body:

- `metodoPagamento` (GPO/REF)
- `telefone` opcional (para GPO)

Header:

- `Idempotency-Key` (obrigatório)

## Segurança

- o pedido é buscado por `findByIdAndTenantId(pedidoId, tenantId)`
- valida que o pedido pertence à mesma Instituicao/Unidade/Mesa do QR quando aplicável
- evita cross-tenant (404)

## Status do pedido

O `StatusFinanceiroPedido` atual não possui `PENDENTE_PAGAMENTO`, então:

- o pedido permanece `NAO_PAGO` após iniciar o pagamento
- a confirmação ficará para o Prompt 9.2 (callback)

## Próximo passo (Prompt 9.2)

- callback tenant-aware resolvendo tenant via externalReference
- confirmar pagamento e marcar Pedido como PAGO (idempotente)
- logs de callback + validação de assinatura/secret

