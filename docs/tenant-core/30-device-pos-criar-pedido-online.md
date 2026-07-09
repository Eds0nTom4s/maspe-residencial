# Prompt 30 — POS/Device cria pedido online (read-write controlado)

## 1) Objetivo

Permitir que um dispositivo POS autenticado (`DevicePrincipal`) crie pedidos online, de forma:

- tenant-safe (escopo vem do device)
- idempotente (Idempotency-Key + clientRequestId)
- auditável (OperationalEventLog com actorType=DEVICE)
- disciplinada por turno (TurnoOperacional aberto por policy)
- com preço/total calculados no servidor
- roteando SubPedido para UnidadeProducao (modelo já existente)

Sem pagamento nesta fase.

## 2) Endpoint

`POST /device/pedidos`

Headers:
- `Authorization: Device <token>`
- `Idempotency-Key: <string>`

Body:
```json
{
  "clientRequestId": "pos-001-20260519-000001",
  "mesaId": 10,
  "qrCodeId": null,
  "observacao": "Sem cebola",
  "itens": [
    { "produtoId": 1, "quantidade": 2, "observacao": "Bem passado" }
  ]
}
```

## 3) Security / tenant-scope

- O request **não aceita** `tenantId/instituicaoId/unidadeAtendimentoId`.
- O escopo vem do `DevicePrincipal`:
  - `tenantId`, `instituicaoId`, `unidadeAtendimentoId`, `dispositivoId`.
- Mesa/QR são validados contra o tenant e contra a unidade do device.

## 4) Capability

Capability exigida:
- `DeviceCapability.CREATE_ORDER`

Mapeamento inicial:
- POS/CHECKOUT/QUIOSQUE incluem `CREATE_ORDER`
- KDS/COZINHA/BAR não incluem `CREATE_ORDER`

## 5) Turno operacional (policy)

Property:
- `consuma.operacao.require-open-turno-for-device-orders=true` (default)

Regras:
- se `true` e não houver `TurnoOperacional` aberto para `tenant+instituicao+unidadeAtendimento`:
  - retorna `409 DEVICE_ORDER_TURNO_REQUIRED`
- se `false`:
  - permite criar pedido sem turno e registra evento `PEDIDO_SEM_TURNO_ABERTO`

## 6) Idempotência

Obrigatório:
- `Idempotency-Key` (header)
- `clientRequestId` (body)

Persistência:
- `device_pedido_idempotency_records` (tenant+device+idempotencyKey/clientRequestId)

Regras:
- mesma chave/ID com mesmo payload → replay (não duplica pedido)
- mesma chave/ID com payload diferente → `409 DEVICE_ORDER_IDEMPOTENCY_CONFLICT`

## 7) Cálculo de preço no servidor

- O backend ignora qualquer preço enviado pelo client (não há campo de preço no request).
- `ItemPedido.precoUnitario` vem de `Produto.preco`.
- Totais são calculados no servidor.

## 8) Produção (SubPedido / UnidadeProducao)

Nesta fase, o comportamento é alinhado ao pedido público por QR:
- cria SubPedidos por cozinha (modelo existente)
- resolve UnidadeProducao por categoria (rota), com fallback para default institucional quando necessário

## 9) Auditoria operacional

Eventos:
- `PEDIDO_CRIADO_DEVICE` (actorType=DEVICE, deviceId, turnoId quando existir)
- `PEDIDO_SEM_TURNO_ABERTO` quando aplicável

## 10) O que fica para futuro

- POS iniciando pagamento (gateway)
- cancelamento/edição de pedido pelo POS
- escrita offline/bidirecional + fila offline
- impressão térmica
- WebSocket/SSE/KDS visual

