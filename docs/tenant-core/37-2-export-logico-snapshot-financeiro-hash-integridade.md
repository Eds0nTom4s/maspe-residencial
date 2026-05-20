# Prompt 37.2 — Export lógico do snapshot financeiro com hash de integridade

## Objetivo

Tornar o snapshot financeiro do turno **tamper-evident** e exportável:

- Calcular hash criptográfico sobre JSON canônico do snapshot financeiro.
- Persistir metadados de integridade em `resumo_json.financeiro.integridade`.
- Expor endpoint read-only para export do snapshot congelado + verificação de integridade.

O export **não recalcula** relatório financeiro e **não altera valores** do snapshot.

## Diferença entre relatório, snapshot e export

- **Relatório (Prompt 37):** recalculável a qualquer momento, reflete o estado atual.
- **Snapshot (Prompt 37.1):** congelado no fecho do turno (histórico).
- **Export (Prompt 37.2):** leitura do snapshot congelado + hash + verificação, sem recálculo.

## Onde o hash é persistido

Em `TurnoOperacional.resumo_json`:

- `financeiro.integridade.hashAlgorithm` (default `SHA-256`)
- `financeiro.integridade.snapshotHash` (hex lowercase)
- `financeiro.integridade.canonicalizationVersion` (default `1.0`)
- `financeiro.integridade.hashGeneratedAt`
- `financeiro.integridade.hashScope` (`resumo_json.financeiro_sem_integridade`)

## Escopo do hash (sem circularidade)

O hash é calculado sobre o objeto `financeiro` **sem** a chave `integridade`.

Assim, evita-se “hash circular”.

## Canonicalização (MVP)

`CanonicalJsonHashService` aplica:

- Ordenação determinística de chaves de objetos (ordem lexicográfica).
- Canonicalização recursiva (objetos/arrays).
- Arrays mantêm ordem original (por isso o snapshot garante ordem determinística em listas pequenas, como `totaisPorMetodo`/`totaisPorOrigem`).

## Endpoints

`GET /tenant/financeiro/turnos/{turnoId}/snapshot/export`

Retorna:
- snapshot congelado (`snapshotFinanceiro`)
- metadados de integridade (`integridade`)
- resultado de verificação (`verificacao`)

### RBAC

Permitidos:
- `TENANT_OWNER`
- `TENANT_ADMIN`
- `TENANT_FINANCE`
- `TENANT_CASHIER` (read-only)

Bloqueados:
- `TENANT_OPERATOR`
- `TENANT_KITCHEN`

## Compatibilidade com snapshots antigos sem hash

Se um turno fechado tiver `resumo_json.financeiro` sem `integridade`:

- o primeiro export gera o hash uma única vez;
- persiste `integridade`;
- registra evento operacional sanitizado;
- **não altera valores financeiros** do snapshot.

Não existe geração retroativa de snapshot para turnos fechados sem `resumo_json.financeiro`.

## Observabilidade / auditoria

Eventos:
- `SNAPSHOT_FINANCEIRO_HASH_GERADO` (on-demand para snapshots antigos sem hash)
- `SNAPSHOT_FINANCEIRO_EXPORTADO` (se `auditExport=true`)
- `SNAPSHOT_FINANCEIRO_INTEGRIDADE_INVALIDA` (quando hash não confere)

Sem payload bruto do gateway e sem snapshot completo no event log.

## Limitações

- Não é assinatura digital certificada.
- Não é documento fiscal.
- Não é conciliação bancária final.
- Não exporta PDF/Excel/CSV.

## Próximo passo recomendado

- Export lógico “assinado” (HMAC da plataforma) e/ou hash do snapshot no fecho com cadeia de auditoria (tamper-evident mais forte), mantendo escopo e sem PDF/Excel.

