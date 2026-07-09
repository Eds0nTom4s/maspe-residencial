# Prompt 24 — Sync incremental robusto: `SyncEnvelope`, ETag, cursor e batch occupancy

## Objetivo
Endurecer o sync read-only para POS/KDS com foco em:
- Contrato estável (envelope) para offline futuro
- Cache HTTP (ETag / If-None-Match → 304)
- Paginação/cursor no catálogo (keyset)
- Redução de queries (batch occupancy de mesas)

Fora do escopo:
- POS criando pedido / iniciando pagamento
- Offline bidirecional / resolução de conflitos
- WebSocket/SSE / impressão

---

## 1) Contrato: `SyncEnvelope<T>`
Todos endpoints `/device/sync/*` retornam:
- `data`
- `syncGeneratedAt`
- `syncVersion`
- `etag`
- `fullSyncRequired`
- `hasMore`
- `nextCursor`
- `mode`
- `warnings`

Quando `If-None-Match` coincide com o ETag calculado:
- retorna `HTTP 304 Not Modified` sem body.

---

## 2) ETag / If-None-Match
ETag é gerado de forma determinística por domínio + escopo do device + filtros relevantes.

Propriedade crítica:
- ETag muda quando o payload observável do endpoint muda.
- ETag não contém tokens/secrets.

---

## 3) Catálogo com cursor (keyset)
Endpoint:
`GET /device/sync/catalogo?updatedSince=&includeInactive=&cursor=&limit=`

Regras:
- `limit` default 100, máximo 500
- `cursor` opaco (base64url de JSON)
- paginação por keyset (`id > lastId`) com `ORDER BY id ASC`

---

## 4) Mesas: batch occupancy
Problema anterior:
- ocupação calculada com N queries (`isOcupada(mesaId)`)

Solução:
- uma query batch em `SessaoConsumoRepository` retorna `mesaIds` com sessão `ABERTA` no tenant.
- `DeviceReadOnlySyncService` monta `Set<Long>` e computa `OCUPADA/DISPONIVEL` sem N queries.

---

## 5) Segurança
- Endpoints de sync exigem `DevicePrincipal` (não aceitam JWT humano).
- Tenant sempre derivado do `DevicePrincipal`.
- Capabilities continuam aplicadas por endpoint.
- Cursor não muda o tenant: o backend sempre revalida escopo pelo principal atual.

---

## Próximos passos recomendados
1) Adicionar ETag em bootstrap/mesas/qrcodes com seeds baseados em `max(updatedAt)` e `count` de forma mais estrita (reduz falso-positivo de 304).
2) Cursor/paginação para QR/mesas se volume crescer.
3) Resumo de “diff” por OperationalEventLog para preparar SSE/WebSocket quando permitido.

