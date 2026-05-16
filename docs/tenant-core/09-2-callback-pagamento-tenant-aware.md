# Prompt 9.2 — Callback de Pagamento Tenant-Aware (AppyPay)

Data: 2026-05-16

## Objetivo

Implementar o callback (webhook) AppyPay de forma **tenant-aware**, com:

- validação de assinatura/secret (HMAC) antes de alterar estados financeiros
- **log bruto** (raw) persistido de todo callback recebido
- **idempotência** (callbacks duplicados não confirmam duas vezes)
- **lock transacional** por `externalReference` para evitar corrida
- atualização segura de `Pagamento` (CONFIRMADO/FALHOU)
- atualização segura de `Pedido.statusFinanceiro` para **PAGO**

Sem implementar nesta fase:

- conciliação financeira completa
- settlement/split
- estornos/reembolsos
- wallet/fundo por QR
- POS/dispositivos

## Estado financeiro do Pedido

Foi adicionado `PENDENTE_PAGAMENTO` em `StatusFinanceiroPedido` para distinguir:

- **NAO_PAGO**: nenhum pagamento iniciado
- **PENDENTE_PAGAMENTO**: pagamento iniciado, aguardando confirmação do gateway
- **PAGO**: confirmado

O fluxo de iniciar pagamento (Prompt 9.1) passa a setar `PENDENTE_PAGAMENTO` após criar `Pagamento`.

O callback confirmado marca:

- `Pagamento.status = CONFIRMADO`
- `Pedido.statusFinanceiro = PAGO` (com `pagoEm`)

## Raw callback log (auditoria)

Tabela: `pagamento_callback_logs`

Persistimos sempre:

- `raw_body` (payload original)
- `headers_json`
- `signature_valid`
- `processing_status` e `processing_error`
- `external_reference` (merchantTransactionId), quando disponível
- associação opcional com `tenant_id` e `pagamento_id` após resolver pagamento

Objetivo: suportar suporte operacional, auditoria e reconciliação futura sem depender apenas de logs de aplicação.

## Resolução e tenant safety

Resolução por:

- `merchantTransactionId` (AppyPay) → `Pagamento.externalReference`

Defesas:

- lock pessimista no pagamento por externalReference
- `Pagamento.tenant` deve existir
- se `Pagamento.pedido != null`, então `Pagamento.tenant == Pedido.tenant` (defesa em profundidade)
- validação de valor: `callback.amount` (centavos) deve bater com `Pagamento.amount`

Se qualquer validação falhar:

- não confirmar o pagamento
- registrar erro no `PagamentoCallbackLog`

## Idempotência do callback

Caso `Pagamento` já esteja `CONFIRMADO`:

- não altera nada (NO-OP)
- registra `PagamentoCallbackLog.processing_status = IGNORED_DUPLICATE`
- retorna HTTP 200 (para evitar retry infinito do gateway)

## Respostas HTTP

- assinatura inválida → **401** (e ainda assim persistimos raw log)
- payload inválido / pagamento não encontrado / duplicado → **200** após registrar log (política anti-retry infinito)

## Migrações

- `V10__pagamento_callback_log_and_pendente_pagamento.sql`
  - atualiza check constraint `pedidos.status_financeiro` para incluir `PENDENTE_PAGAMENTO`
  - cria `pagamento_callback_logs`

## Próximos passos (Prompt 9.3+)

- callback tenant-aware para outros tipos (recarga/subscrição) com modelagem explícita
- reconciliação/monitoramento de pagamentos pendentes (REF)
- estorno/reembolso controlado e auditável
- painel admin para inspeção de callback logs por tenant

