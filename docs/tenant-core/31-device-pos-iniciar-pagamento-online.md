# Prompt 31 — POS/Device iniciar pagamento online (read-write financeiro controlado)

## Objetivo

Permitir que um POS/dispositivo operacional autenticado inicie um pagamento online para um pedido existente, de forma:

- tenant-aware (escopo sempre do `DevicePrincipal`)
- device-aware (auditoria `actorType=DEVICE`)
- idempotente (retry seguro)
- consistente (valor calculado no servidor)
- sem alterar o callback AppyPay existente (continua sendo a fonte de confirmação)

## O que esta fase **não** implementa

- callback novo ou alterações no callback AppyPay
- polling do gateway
- offline, numerário, split/settlement, conciliação final
- refund/cancelamento
- WebSocket/SSE, impressão

## Endpoint

`POST /device/pedidos/{pedidoId}/pagamentos`

Headers:

- `Authorization: Device <deviceToken>`
- `Idempotency-Key: <string>`

Body (`DeviceIniciarPagamentoRequest`):

- `clientRequestId` (obrigatório)
- `metodoPagamento` (obrigatório, enum do AppyPay)
- `telefoneCliente` (opcional)
- `descricao` (opcional)
- `returnUrl` (opcional)

Response (`DevicePagamentoResponse`):

- `pagamentoId`, `pedidoId`, `numeroPedido`
- `tenantId`, `instituicaoId`, `unidadeAtendimentoId`, `turnoOperacionalId`
- `valor`, `moeda`, `metodoPagamento`, `statusPagamento`
- `gateway`, `externalReference`, `checkoutUrl`, `entidade`, `referencia`
- `idempotentReplay`, `criadoEm`

## Segurança e escopo

- `DevicePrincipal` é a fonte de verdade para `tenantId/instituicaoId/unidadeAtendimentoId`.
- JWT humano não é aceito em `/device/**`.
- Capability obrigatória: `DeviceCapability.INITIATE_PAYMENT`.
- Pedido deve pertencer ao mesmo `tenant` e à mesma `unidadeAtendimento` do device (cross-tenant/unidade retorna 404).

## Política de turno

Property:

- `consuma.operacao.require-open-turno-for-device-payments=true` (default)

Regra:

- Sem turno ABERTO na unidade do device: bloqueia com `409 DEVICE_PAYMENT_TURNO_REQUIRED`.
- Se o pedido estiver associado a um turno diferente do turno aberto: bloqueia com `409 DEVICE_PAYMENT_INVALID_STATUS`.

## Idempotência

Tabela: `device_pagamento_idempotency_records` (Flyway `V22__device_pagamento_idempotency.sql`).

Regras:

- `Idempotency-Key` obrigatório por request (retry HTTP).
- `clientRequestId` obrigatório por payload (retry local do POS).
- Mesma chave/mesmo payload: replay retorna o mesmo pagamento com `idempotentReplay=true`.
- Mesma chave com payload diferente: `409 DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT`.

## Integração AppyPay

- Inicia cobrança via `AppyPayClient.createCharge(...)`.
- `externalReference` é gerado de forma compacta (<= 15 chars) via `PaymentReferenceService.gerarReferenciaPedidoDevice(...)`.
- Callback existente continua sendo responsável por confirmar (`StatusPagamentoGateway.CONFIRMADO`) e atualizar status financeiro.

## Auditoria operacional

Registra `OperationalEventLog`:

- `OperationalEventType.PAGAMENTO_INICIADO_DEVICE`
- `actorType=DEVICE` com `deviceId`
- com referência a `turno/pedido/pagamento` quando aplicável

## Erros (DeviceErrorResponse)

Endpoints `/device/**` (exceto `/device/sync/**`) retornam `DeviceErrorResponse` padronizado.

Códigos relevantes:

- `DEVICE_PAYMENT_IDEMPOTENCY_KEY_REQUIRED`
- `DEVICE_PAYMENT_CLIENT_REQUEST_ID_REQUIRED`
- `DEVICE_PAYMENT_TURNO_REQUIRED`
- `DEVICE_PAYMENT_PEDIDO_ALREADY_PAID`
- `DEVICE_PAYMENT_ALREADY_PENDING`
- `DEVICE_PAYMENT_GATEWAY_ERROR`
- `DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT`

## Próximos passos (fora do escopo)

- POS iniciar pagamento + acompanhar status (polling/control plane) **sem** mexer no callback
- permitir múltiplas tentativas/expiração de pending com política explícita
- suportar numerário/manual cash
- impressão e KDS visual

