# Prompt 26 — BootstrapVersion dedicado + fullSyncRequired por domínio + cursor QR/Mesas + diff incremental da fila

Data: 2026-05-18

## Objetivo
Fortalecer o sync read-only para POS/KDS antes de habilitar qualquer escrita por dispositivo, cobrindo:

- ETag dedicado do bootstrap (`/device/sync/bootstrap`) sem depender de “proxy ETag”.
- Heurísticas consistentes de `fullSyncRequired` por domínio (catálogo/mesas/QR/produção).
- Cursor opcional (keyset) para mesas e QR, evitando snapshots grandes.
- Diff incremental da fila de produção via `OperationalEventLog` (read-only), preparado para offline futuro.

## Visão geral do desenho
### Bootstrap (config)
O bootstrap muda quando mudam:
- tenant / instituição / unidadeAtendimento / unidadeProducao (quando aplicável)
- status/tipo/tokenVersion do dispositivo
- capabilities derivadas do dispositivo
- versão do contrato de sync (`consuma.sync.bootstrap.contract-version`)

Por isso o ETag e `syncVersion` do bootstrap agora são computados com seed próprio (BOOTSTRAP), e `If-None-Match` pode retornar `304` com segurança.

### fullSyncRequired por domínio
Regras mínimas adotadas:
- `updatedSince` muito antigo → `fullSyncRequired=true` (`CLIENT_TOO_OLD`)
- incremental solicitado com `updatedAt` ausente em registros relevantes → `UPDATED_AT_UNRELIABLE`
- mudanças desde `updatedSince` acima do threshold por domínio → `TOO_MANY_CHANGES`

Quando `fullSyncRequired=true`, **não** retornamos `304`, mesmo se `If-None-Match` coincidir.

### Cursor (keyset)
Para domínios de snapshot (mesas/QR) implementamos paginação por cursor opaco (Base64 JSON), validado por:
- `tenantId`
- `domain`
- `issuedAt` (expira por `consuma.sync.cursor-expiration-hours`)
- filtros (`updatedSince` e `unidadeAtendimentoId` quando aplicável)

**Importante:** requests com `cursor` não retornam `304` (página 2 precisa de body mesmo com ETag estável).

### Diff incremental da fila (produção)
Novo endpoint read-only:
- `GET /device/sync/producao/fila/diff?sinceEventId=&limit=`

Ele retorna:
- eventos operacionais (`OperationalEventLog`) no escopo da `unidadeProducao` do device
- `subPedidosAtualizados` (estado atual) para os `subPedidoIds` afetados

Se `sinceEventId` não existir no escopo do device, o servidor retorna:
- `fullSyncRequired=true`
- `fullSyncReason=VERSION_MISMATCH`

## Endpoints
- `GET /device/sync/bootstrap` (ETag dedicado)
- `GET /device/sync/catalogo` (cursor já existente)
- `GET /device/sync/mesas` (cursor opcional `cursor/limit`)
- `GET /device/sync/qrcodes` (cursor opcional `cursor/limit`)
- `GET /device/sync/producao`
- `GET /device/sync/producao/fila`
- `GET /device/sync/producao/fila/diff`

## Properties relevantes
- `consuma.sync.bootstrap.contract-version=v1`
- `consuma.sync.max-incremental-age-days=7`
- `consuma.sync.catalog.max-incremental-changes=1000`
- `consuma.sync.mesas.max-incremental-changes=500`
- `consuma.sync.qrcodes.max-incremental-changes=500`
- `consuma.sync.producao.max-incremental-changes=500`
- `consuma.sync.cursor-expiration-hours=24`
- `consuma.sync.mesas.default-limit=500`
- `consuma.sync.mesas.max-limit=1000`
- `consuma.sync.qrcodes.default-limit=500`
- `consuma.sync.qrcodes.max-limit=1000`
- `consuma.sync.fila.diff.default-limit=200`
- `consuma.sync.fila.diff.max-limit=1000`

## Limitações (intencionais)
- Sync continua 100% read-only.
- Sem POS criando pedido ou iniciando pagamento.
- Sem offline bidirecional e sem resolução de conflitos.
- Sem WebSocket/SSE e sem impressão.

## Próximos passos sugeridos
- Estender o diff incremental para catálogo/mesas/QR (event-based) quando necessário.
- Introduzir “rotina de abertura/fecho” (checklist) e métricas operacionais de operação.
- Só depois, permitir escrita online por POS com idempotência + trilha de eventos.

