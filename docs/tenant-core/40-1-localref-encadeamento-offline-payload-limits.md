# Prompt 40.1 — localRef + encadeamento seguro + payload limits

## Objetivo
Permitir que o POS envie, no **mesmo batch offline**, uma sequência segura de comandos com dependências:

1. `CREATE_PEDIDO_POS`
2. `CREATE_ORDEM_PAGAMENTO_MANUAL` referenciando o pedido criado no batch via `pedidoClientRequestId`
3. `CONFIRM_MANUAL_PAYMENT` referenciando a ordem criada no batch via `ordemPagamentoClientRequestId`

Sem quebrar idempotência e com limites explícitos de payload/tamanho.

## `clientRequestId` vs `localRef`
- `clientRequestId`: **chave idempotente** do comando (obrigatória).
- `localRef`: referência local opcional **para resolver dependências no batch**.

MVP:
- `localRef` default = `clientRequestId`.
- Forward refs são bloqueadas por default (`allowForwardLocalRefs=false`).

## Como a resolução funciona
Durante `POST /device/offline-sync/batch`:

1. O servidor valida tamanho do batch e payload.
2. Ordena comandos por `localSequence` e `localCreatedAt`.
3. Para cada comando:
   - persiste `device_offline_commands` (idempotência por `tenant + device + clientRequestId`);
   - calcula `payloadHash` (SHA-256 em JSON canônico);
   - se replay:
     - hash igual → `DUPLICATE` (retorna resultado persistido);
     - hash diferente → `CONFLICT` (`IDEMPOTENCY_CONFLICT`);
4. Para comandos com dependências:
   - resolve `dependsOn`/`*ClientRequestId`:
     - primeiro tenta resolver no batch atual (mapa localRef→entidade);
     - se não existir, busca no histórico `device_offline_commands` do **mesmo device**.
   - injeta IDs resolvidos no payload antes de executar handler:
     - `pedidoClientRequestId` → `pedidoId`
     - `ordemPagamentoClientRequestId` → `ordemPagamentoId`
   - valida tipo:
     - `pedidoClientRequestId` deve resolver para `PEDIDO`
     - `ordemPagamentoClientRequestId` deve resolver para `ORDEM_PAGAMENTO`

## Encadeamento suportado (MVP)
- Pedido → Ordem manual (`CASH/TPA`)
- Ordem manual → Confirmação manual (`CASH/TPA`)

AppyPay e OTP continuam **online-only** (proibidos no offline).

## localRef entre batches
É permitido resolver dependência em batch posterior desde que:
- mesmo `tenantId`;
- mesmo `deviceId`;
- o comando referenciado exista e tenha status `APPLIED` ou `DUPLICATE`;
- exista `createdEntityType/createdEntityId`.

## Payload limits
Properties adicionadas:
- `consuma.device.offline-sync.max-batch-payload-bytes`
- `consuma.device.offline-sync.max-command-payload-bytes`
- `consuma.device.offline-sync.max-pedido-items`
- `consuma.device.offline-sync.max-local-ref-depth`
- `consuma.device.offline-sync.allow-forward-local-refs`

Regras:
- payload bytes são calculados no backend (UTF-8 do JSON canônico).
- batch e comando acima do limite → erro.
- pedido offline com itens acima de `maxPedidoItems` → erro.
- dependências com profundidade acima de `maxLocalRefDepth` → erro.
- forward refs → bloqueado por default.
- dependência circular → erro.

## Idempotência preservada
`clientRequestId` continua sendo a chave idempotente por device.

`localRef` não altera a semântica: é apenas um mecanismo de resolução de dependência em runtime.

## Limitações
- Não implementa forward refs por default (pode ser habilitado por property, mas não é o modo recomendado no MVP).
- Não implementa rollback do batch.
- Não implementa AppyPay/OTP offline.

## Próximo passo sugerido (40.2)
- “localRef session”/sync session header persistido para observabilidade e troubleshooting.
- suporte a múltiplas dependências por comando (além de 1 principal).

