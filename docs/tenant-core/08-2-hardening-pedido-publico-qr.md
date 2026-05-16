# Prompt 8.2 — Hardening do pedido público por QR (idempotência + sequência + sessão)

Data: 2026-05-16

## Objetivo

Endurecer o endpoint público de criação de pedido por QR antes de implementar pagamento:

1. **Idempotência**: retry/duplo-clique não pode duplicar pedido.
2. **Numeração concorrência-safe**: eliminar `count()+1`.
3. **Sessão por mesa**: regras robustas de reuso/criação.

## O que não entra (por design)

- Pagamento/AppyPay
- callback tenant-aware
- débito/recarga de FundoConsumo
- remoção de QR legado / `getInstituicaoAtiva()`

## Idempotência

### Contrato

- Header obrigatório: `Idempotency-Key`
- Body opcional: `idempotencyKey`
- Header tem prioridade sobre body.
- Se faltar: **400** — “Idempotency-Key é obrigatório para criar pedido por QR.”

### Regras

- UNIQUE `(tenant_id, qr_code_operacional_id, idempotency_key)`
- Hash canônico `request_hash` (SHA-256) do payload relevante (cliente + itens).
- Mesma chave + mesmo hash:
  - retorna o **mesmo pedido** (deduplicação)
- Mesma chave + hash diferente:
  - **409 Conflict** — “Idempotency-Key reutilizada com payload diferente.”
- Status:
  - PROCESSING / COMPLETED / FAILED

## Numeração concorrência-safe

Modelo:

- contador diário por tenant (`tenant_id` + `data_referencia`)
- lock pessimista para garantir unicidade sob concorrência

Formato do número:

`PED-{tenantCode}-{yyyyMMdd}-{seq6}`

Exemplo:

`PED-LEGACY-20260516-000123`

## Sessão por mesa

Hardening aplicado para QR tipo `MESA`:

- busca todas as sessões `ABERTA` da mesa
- se existir mais de uma: falha (erro operacional)
- se existir uma: valida instituição e reutiliza
- se não existir: cria sessão mínima

Para outros tipos de QR:

- cria sessão mínima por pedido

Sessão mínima:

- `modoAnonimo=true`, `status=ABERTA`, `tipoSessao=POS_PAGO`
- vinculada à Instituição/Unidade/Mesa do QR
- cria `FundoConsumo` com saldo 0 (invariante do domínio)

## Migração

- `V8__hardening_pedido_publico_qr_idempotencia_sequence.sql`
  - cria `public_qr_order_requests`
  - cria `pedido_sequence_counters`

## Testes

PostgreSQL/Testcontainers:

- Idempotency-Key obrigatório (400)
- retry com mesma chave/payload retorna mesmo pedido
- mesma chave/payload diferente retorna 409
- numeração concorrente por tenant gera números distintos
- QR tipo MESA reutiliza sessão ABERTA

## Próximo passo (Prompt 8.3 ou 9)

- idempotência “end-to-end” com consulta pública por idempotencyKey (opcional)
- pagamento tenant-aware (fase separada)

