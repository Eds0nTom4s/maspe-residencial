# Prompt 27 — Cursor HMAC + erro padronizado de sync + diff de fila com UPSERT/REMOVE

Data: 2026-05-18

## Objetivo
Endurecer o sync read-only para POS/KDS antes de habilitar qualquer escrita:

- Cursor opaco **assinado com HMAC** para impedir manipulação silenciosa.
- Erros de `/device/sync/*` padronizados via `SyncErrorResponse`.
- Diff da fila com semântica explícita de atualização (`UPSERT`) vs remoção (`REMOVE`).

## 1) Cursor assinado (HMAC)
### Formato
`cursor = base64url(payloadJson) + "." + base64url(hmacSha256(base64url(payloadJson), secret))`

O payload contém `tenantId`, `domain`, `issuedAt` e os filtros relevantes (por exemplo `updatedSince`, `includeInactive`, `unidadeAtendimentoId`, `lastId`).

### Properties
- `consuma.sync.cursor.hmac-secret`
- `consuma.sync.cursor.require-signature` (default `true`)

### Regras
- Cursor sem assinatura é rejeitado quando `require-signature=true`.
- Cursor adulterado falha com erro padronizado (HTTP 400).
- Cursor expirado segue sendo tratado no fluxo (reinício controlado + warnings onde aplicável).

## 2) Erros padronizados de sync
### DTO
`SyncErrorResponse` padroniza:
- `code`
- `message`
- `fullSyncRequired` / `fullSyncReason`
- `recoverable` + `action` (para orientar o app)
- `serverTime`

### Handler
`DeviceSyncExceptionHandler` aplica tratamento apenas em paths `/device/sync/*`, garantindo:
- 400 para cursor inválido/assinalado incorretamente
- 401 para device não autenticado
- 403 para capability insuficiente
- 409 para escopo ambíguo

## 3) Diff da fila com UPSERT/REMOVE
### Endpoints
- `GET /device/sync/producao/fila/diff`

### Semântica
O response passa a incluir `updates`:
- `UPSERT` quando o subpedido permanece visível na fila do KDS
- `REMOVE` quando o subpedido entra em estado terminal (`ENTREGUE`/`CANCELADO`)

Compatibilidade: `subPedidosAtualizados` permanece no response nesta fase.

## Limitações intencionais
- Sync continua 100% read-only.
- Sem POS criando pedido/iniciando pagamento.
- Sem offline bidirecional, WebSocket/SSE, impressão.

## Próximos passos sugeridos
- Assinatura/rotação de segredo por ambiente (bloquear secret default em prod).
- Evoluir diff incremental para outros domínios (sem criar change-log pesado no core).
- Só depois permitir escrita online por POS com idempotência + trilha de eventos.

