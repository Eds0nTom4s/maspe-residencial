# Prompt 36 — Gestão de Consumo Físico: FundoConsumo + OrdemPagamento manual (CASH/TPA)

## Objetivo
Permitir operação presencial **sem dependência de AppyPay**, suportando:
- consumo anónimo por QR/código;
- criação de **OrdemPagamento** manual para **CASH/TPA físico**;
- confirmação manual por **POS autorizado** (device), com idempotência e locks;
- crédito síncrono do **FundoConsumo** após confirmação manual;
- pagamento manual de **Pedido** (quando solicitado via ordem manual);
- bloqueio/desbloqueio/encerramento de consumo/fundo pelo tenant;
- reimpressão lógica (QR/conta/comprovativo), sem driver de impressora.

## Princípio central
Existem **dois fluxos separados**:
- **AppyPay**: inicia cobrança → callback/polling confirma → sistema atualiza pagamento/pedido.
- **Manual (CASH/TPA)**: cria OrdemPagamento → POS confirma → sistema confirma e aplica efeito **imediato**.

Nunca misturar confirmação manual com AppyPay.

## Endpoints
### Público (QR operacional)
- `GET /public/q/{token}/consumos/opcoes`
- `POST /public/q/{token}/consumos/anonimo`
- `POST /public/q/{token}/consumos/{codigoConsumo}/carregamentos` (CASH/TPA)
- `POST /public/q/{token}/pedidos/{pedidoId}/ordens-pagamento-manual` (CASH/TPA)
- `GET /public/ordens-pagamento/{token}/status`

### Device (POS)
- `GET /device/ordens-pagamento/{token}` (scan)
- `POST /device/ordens-pagamento/{ordemId}/confirmar-manual` (Idempotency-Key + clientRequestId)
- `POST /device/consumos/{codigoConsumo}/reimprimir-qr`
- `POST /device/consumos/{codigoConsumo}/reimprimir-conta`
- `POST /device/consumos/ordens-pagamento/{ordemId}/reimprimir-comprovativo`

### Tenant
- `POST /tenant/consumos/{codigoConsumo}/bloquear`
- `POST /tenant/consumos/{codigoConsumo}/desbloquear`
- `POST /tenant/consumos/{codigoConsumo}/encerrar`

## Turno obrigatório
`consuma.operacao.require-open-turno-for-manual-payments=true` por default.

Sem turno aberto, o POS não confirma CASH/TPA (409).

## Idempotência e locks
- `Idempotency-Key` + `clientRequestId` na confirmação manual pelo device.
- `PESSIMISTIC_WRITE` na OrdemPagamento (evita corrida de dupla confirmação).
- Crédito no FundoConsumo é idempotente por `merchantTransactionId = ORD-{ordemId}` (ledger append-only).

## Auditoria (OperationalEventLog)
Eventos principais:
- `ORDEM_PAGAMENTO_CRIADA`
- `ORDEM_PAGAMENTO_CONFIRMADA_MANUAL`
- `FUNDO_CONSUMO_CREDITADO_MANUAL`
- `PAGAMENTO_CASH_CONFIRMADO_DEVICE` / `PAGAMENTO_TPA_CONFIRMADO_DEVICE`
- `FUNDO_CONSUMO_BLOQUEADO` / `FUNDO_CONSUMO_DESBLOQUEADO`
- `SESSAO_CONSUMO_ENCERRADA`
- reimpressões: `CONSUMO_QR_REIMPRESSO_DEVICE`, `CONTA_REIMPRESSA_DEVICE`, `COMPROVATIVO_ORDEM_REIMPRESSO_DEVICE`

## Limitações (intencionais nesta fase)
- Sem driver real de impressora (apenas payload lógico).
- Sem offline.
- Sem refund/settlement/conciliação contábil final.
- Sem alterações no callback/polling AppyPay.

