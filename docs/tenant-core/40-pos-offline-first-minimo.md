# Prompt 40 — POS offline-first mínimo

## Objetivo
Adicionar um mecanismo **offline-first mínimo** para POS/device: o POS pode acumular **comandos locais** durante instabilidade e depois **sincronizar em batch** com o backend, com **idempotência forte** e **auditoria**, sem transformar o produto em “offline total”.

Este prompt **não** altera callback/polling AppyPay, nem WORM/Evidence Bundle.

## Offline mínimo vs. offline total
Offline mínimo:
- aceita **somente comandos seguros**;
- exige `clientRequestId` por comando;
- garante idempotência por `tenant + device + clientRequestId`;
- sincroniza em ordem (`localSequence`/`localCreatedAt`);
- rejeita comandos antigos demais;
- **não** processa gateways externos offline (AppyPay continua online-only).

Offline total (fora do escopo):
- banco local no POS;
- reconciliação bidirecional;
- CRDT/merge complexo;
- jobs/fila externa.

## Comandos permitidos (MVP)
Endpoint batch aceita apenas:
- `CREATE_PEDIDO_POS`
- `CREATE_ORDEM_PAGAMENTO_MANUAL` (somente `CASH`/`TPA`)
- `CONFIRM_MANUAL_PAYMENT` (somente `CASH`/`TPA`)
- `REGISTER_LOCAL_ACTIVITY` (sem efeitos financeiros)

Comandos explicitamente proibidos no MVP:
- qualquer comando de gateway (`APPYPAY`) e fluxos OTP;
- fechamento de turno, snapshot, evidence bundle;
- configurações/admin (policies/capabilities).

## Como o batch sync funciona
`POST /device/offline-sync/batch`

1. Valida `OFFLINE_SYNC` no device.
2. Valida `maxBatchSize`.
3. Ordena comandos por `localSequence`, depois `localCreatedAt`.
4. Para cada comando:
   - calcula `payloadHash` (SHA-256) sobre JSON canônico;
   - persiste `device_offline_commands` com UNIQUE `(tenant_id, dispositivo_operacional_id, client_request_id)`;
   - se já existir:
     - `payloadHash` igual → responde `DUPLICATE` com resultado anterior;
     - `payloadHash` diferente → responde `CONFLICT` (`IDEMPOTENCY_CONFLICT`).
   - se novo:
     - marca `PROCESSING`;
     - valida capability específica do comando (`OFFLINE_CREATE_ORDER`, `OFFLINE_CREATE_MANUAL_PAYMENT_ORDER`, `OFFLINE_CONFIRM_MANUAL_PAYMENT`);
     - executa handler do comando reutilizando serviços existentes;
     - grava `result_json` e `created_entity_*` em caso de sucesso.

## Idempotência e conflitos
Idempotência é por:
- `tenantId + deviceId + clientRequestId`.

Regras:
- replay com mesmo payload → não duplica entidade; retorna resultado persistido.
- replay com payload diferente → `IDEMPOTENCY_CONFLICT` (status `CONFLICT`).

## CASH/TPA offline e turno aberto
Mesmo offline no POS, o efeito financeiro só acontece no **sync**.

Para `CONFIRM_MANUAL_PAYMENT`:
- valida policies atuais (tenant/unidade/device);
- valida capability do device;
- exige turno aberto no momento do sync (regra já existente).

## AppyPay continua online-only
Offline sync rejeita:
- `metodo=APPYPAY` em comandos manuais;
- qualquer comando de gateway.

Motivos:
- dependência de confirmação externa;
- risco de duplicação/concorrência;
- necessidade de consistência financeira centralizada.

## Segurança
- Tenant/unidade/device são inferidos de `DevicePrincipal` (não aceitar `tenantId` do payload como fonte de verdade).
- Allowed command types vêm de `consuma.device.offline-sync.allowed-command-types`.
- Commands muito antigos são rejeitados por `maxOfflineAgeMinutes`.

## Auditoria (sanitizada)
Eventos adicionados:
- `DEVICE_OFFLINE_SYNC_RECEIVED`
- `DEVICE_OFFLINE_COMMAND_APPLIED`
- `DEVICE_OFFLINE_COMMAND_DUPLICATE`
- `DEVICE_OFFLINE_COMMAND_REJECTED`
- `DEVICE_OFFLINE_COMMAND_CONFLICT`
- `DEVICE_OFFLINE_COMMAND_FAILED`
- `DEVICE_OFFLINE_SYNC_COMPLETED`

Sem payload completo, sem deviceToken, sem OTP, sem dados sensíveis.

## Endpoints
- `POST /device/offline-sync/batch`
- `GET /device/offline-sync/commands/{clientRequestId}`
- `GET /device/offline-sync/capabilities`

## Limitações
- Não implementa dependências locais (ex.: comando B referenciar entidade criada por comando A via `clientRequestId`).
- Não implementa rollback/cancelamento do batch.
- Não implementa offline para AppyPay/OTP.
- Não implementa UI.

## Próximo passo recomendado (Prompt 40.1)
- Suporte a dependências locais (localRef) e encadeamento seguro de comandos (pedido → ordem → confirmação).
- Policy de tamanho máximo de payload por comando.
- Persistência de “sync sessions” agregadas (header), se necessário.

