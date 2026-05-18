# Prompt 25 — SyncVersion SQL-based + fullSyncRequired heurístico

## Objetivo
Reduzir risco de `304 Not Modified` falso e preparar base para sync incremental/offline futuro, substituindo ETags “seed-based” por versões calculadas via **agregações reais**:
- `count(*)`
- `max(updatedAt)` / `max(createdAt)`
- `max(event.createdAt)` quando status/eventos influenciam payload (fila de produção)

Além disso:
- ativar heurísticas de `fullSyncRequired` (idade do incremental, updatedAt ausente, muitas mudanças).

## Componentes adicionados
- `DeviceSyncVersionService`
  - Calcula `syncVersion` e `etag` por domínio via queries agregadas.
  - Decide `fullSyncRequired`/`fullSyncReason` e `warnings` com base em heurísticas.

## Domínios cobertos
- Catálogo: `CategoriaProduto` + `Produto`
- Mesas: `Mesa` + sessões `ABERTA` (ocupação)
- QR Codes: `QrCodeOperacional` (ativo e não revogado)
- Produção: `UnidadeProducao` + `RotaProducaoCategoria`
- Fila: `SubPedido` + `OperationalEventLog` (max createdAt por unidade)

## Heurísticas (MVP)
- `updatedSince` muito antigo → `fullSyncRequired=true` (`CLIENT_TOO_OLD`)
- `updatedAt` ausente em registros relevantes e incremental solicitado → `UPDATED_AT_UNRELIABLE`
- muitas alterações desde updatedSince (catalog) → `TOO_MANY_CHANGES`
- cursor expirado no catálogo → `CURSOR_EXPIRED` (reinicia paginação + warning)

Properties:
- `consuma.sync.max-incremental-age-days` (default 7)
- `consuma.sync.max-incremental-changes` (default 1000)
- `consuma.sync.cursor-expiration-hours` (default 24)

## ETag / 304
Regra:
- Só retorna `304` quando `fullSyncRequired=false` e `If-None-Match` coincide.
- Se `fullSyncRequired=true`, sempre retorna `200` com envelope e reason/warnings.

## Cursor (catálogo)
O cursor do catálogo passa a carregar:
- `domain`, `tenantId`, `issuedAt`, `lastProdutoId`, `updatedSince`, `includeInactive`

Validações:
- tenantId e domain devem bater
- cursor expira por horas (property)
- filtros incompatíveis → erro

## Próximos passos recomendados
- Implementar `computeBootstrapVersion` dedicado (tenant/instituição/unidade/dispositivo) com agregação “max(updatedAt)”.
- Expandir “muitas alterações” para mesas/qr/produção quando necessário.
- Cursor para QR/mesas caso volume ultrapasse threshold real (plano).

