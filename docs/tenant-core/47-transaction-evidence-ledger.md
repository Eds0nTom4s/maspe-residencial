# Prompt 47 — Transaction Evidence Ledger (append-only)

## Objetivo
Adicionar um **ledger transacional interno, append-only**, para evidenciar eventos críticos por tenant com:

- payload canônico (sanitizado);
- `payloadHash` + `previousHash` + `eventHash`;
- autenticação por `HMAC-SHA256` com `keyVersion`;
- sequência incremental por tenant (`ledgerSequence`);
- verificação de integridade (cadeia / gaps / assinatura);
- resumo no Evidence Bundle (`transactionLedgerEvidence`).

## Escopo
- Persistência de eventos de evidência em tabelas próprias.
- Canonicalização determinística de payload (JSON com chaves ordenadas).
- Hashing e HMAC com versionamento de chave.
- Registro idempotente via `idempotencyKey`.
- Verificação por período (`verifyTenantLedger`).
- Endpoints tenant/admin para listar eventos e runs de verificação.
- Integração no Evidence Bundle (seção de resumo).
- Auditoria sanitizada via `OperationalEventLog`.

## Não escopo
- Substituir Evidence Bundle existente.
- Submissão fiscal oficial (AGT) ou assinatura legal.
- Blockchain/Web3.
- Exposição de payload bruto com PII (AppyPay raw, cartões, telefone completo, tokens, headers).
- Bloquear operação se ledger falhar (MVP não faz rollback do negócio).

## Modelo de domínio

### Entidades
- `TransactionEvidenceLedgerState`: estado do ledger por tenant (última sequência/hash).
- `TransactionEvidenceEvent`: evento registrado (append-only) com `canonicalPayloadJson`, hashes e assinatura HMAC.
- `TransactionEvidenceVerificationRun`: execução de verificação por período.
- `TransactionEvidenceVerificationIssue`: issues detectadas (gap, broken chain, hash/signature mismatch).

### Chaves / idempotência
- Uniques:
  - `(tenant_id, ledger_sequence)`
  - `(tenant_id, idempotency_key)`
- Idempotency key (para eventos derivados do OperationalEventLog):
  - `tenant:{tenantId}:oplog:{logId}:tx-ledger:v1`

## Canonical payload
- `schemaVersion`: `consuma.evidence.tx-ledger.canonical-payload-version` (default: `tx-evidence-v1`)
- Datas em UTC (`OffsetDateTime` textual).
- `BigDecimal` normalizado para string sem notação científica.
- Campos sanitizados: o listener mascara chaves sensíveis em `metadataJson` antes de entrar no payload canônico.

## Hashing e assinatura (HMAC)
- `canonicalPayloadHash = SHA-256(canonicalPayloadJson)`
- `eventHash = SHA-256(canonicalString(..., previousEventHash, keyVersion))`
- `hmacSignature = HMAC-SHA256(eventHash, secret(keyVersion))`
- `keyVersion` é persistido no evento.

### Provider de chave
- A chave **não** é persistida no banco.
- Lookup principal via env var:
  - `CONSUMA_TX_EVIDENCE_HMAC_KEY_V{keyVersion}`
- Fallback dev/test via property (não usar em produção):
  - `consuma.evidence.tx-ledger.dev-hmac-secret`

## Registro de eventos (AFTER_COMMIT)
O ledger é alimentado por eventos AFTER_COMMIT derivados do `OperationalEventLog`:

- `OperationalEventLogService` publica `OperationalEventLoggedEvent` após salvar o log.
- Listener `TransactionEvidenceOnOperationalEventLoggedListener`:
  - filtra eventos críticos (PAYMENT/FISCAL/INVENTORY/BILLING/EVIDENCE);
  - ignora eventos `TRANSACTION_EVIDENCE_*` para evitar recursão;
  - sanitiza metadata;
  - chama `TransactionEvidenceLedgerService.recordEvidenceEvent(...)` (REQUIRES_NEW).

Falhas no ledger são logadas e **não** revertem operação já confirmada (listener AFTER_COMMIT).

## Verificação de cadeia
`TransactionEvidenceVerificationService.verifyTenantLedger(tenantId, periodStart, periodEnd)`:

- carrega eventos do período (ordenado por `ledgerSequence`);
- detecta:
  - `SEQUENCE_GAP`
  - `BROKEN_CHAIN` (previousHash incorreto)
  - `HASH_MISMATCH` (payloadHash/eventHash divergentes)
  - `SIGNATURE_MISMATCH` (HMAC inválido ou chave indisponível)
- persiste `TransactionEvidenceVerificationRun` + `TransactionEvidenceVerificationIssue`.

## Endpoints (tenant/admin)
Base: `/tenant/evidence/transaction-ledger`

- `GET /events` (filtros: `eventType`, `sourceModule`, `sourceEntityType`, `sourceEntityId`, `occurredFrom/To`, `sequenceFrom/To`)
- `GET /events/{eventId}`
- `GET /state`
- `POST /verify`
- `GET /verification-runs`
- `GET /verification-runs/{runId}`
- `GET /verification-runs/{runId}/issues`

RBAC: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE` (platform admin já é permitido pelo `TenantGuard`).

## Integração com Evidence Bundle
O Evidence Bundle (`SnapshotFinanceiroEvidenceBundleResponse`) ganhou:

- `transactionLedgerEvidence`

Gerado por `TransactionLedgerEvidenceService`:

- total de eventos do período do turno;
- sequência/hash inicial/final do período;
- hash do estado do ledger;
- último run de verificação (se existir) e warnings.

## Auditoria (OperationalEventLog)
Eventos adicionados em `OperationalEventType`:

- `TRANSACTION_EVIDENCE_EVENT_RECORDED`
- `TRANSACTION_EVIDENCE_DUPLICATE_IGNORED`
- `TRANSACTION_EVIDENCE_RECORDING_FAILED`
- `TRANSACTION_EVIDENCE_LEDGER_VERIFICATION_STARTED|COMPLETED|FAILED`
- `TRANSACTION_EVIDENCE_LEDGER_BROKEN_CHAIN_DETECTED`
- `TRANSACTION_EVIDENCE_LEDGER_SEQUENCE_GAP_DETECTED`
- `TRANSACTION_LEDGER_EVIDENCE_ATTACHED_TO_BUNDLE`

Payload é sanitizado; não registrar segredos/chaves ou payloads sensíveis.

## Propriedades
- `consuma.evidence.tx-ledger.enabled=true`
- `consuma.evidence.tx-ledger.key-version=1`
- `consuma.evidence.tx-ledger.canonical-payload-version=tx-evidence-v1`
- `consuma.evidence.tx-ledger.dev-hmac-secret` (dev/test)

## Limitações (MVP)
- O ledger é alimentado a partir do `OperationalEventLog` (não por todos os eventos do sistema).
- Verificação atual é por período de `occurredAt` e pode não cobrir eventos fora do intervalo (por design).
- Não há mecanismo de “pending outbox” dedicado; o listener registra diretamente AFTER_COMMIT.
- Não há rotação/armazenamento em KMS/HSM (apenas interface/provider via env + fallback dev/test).
- Alguns testes de integração com PostgreSQL/Testcontainers podem ser skipados em ambientes sem Docker.

## Comandos executados
- `mvn -q -DskipTests compile`
- `mvn test`

## Checklist de aceitação (Prompt 47)
- [x] Existe `TransactionEvidenceEvent`
- [x] Existe `TransactionEvidenceLedgerState`
- [x] Existe `TransactionEvidenceVerificationRun` e `TransactionEvidenceVerificationIssue`
- [x] Sequência por tenant + `previousHash` encadeando
- [x] `payloadHash` + `eventHash` determinísticos
- [x] HMAC com `keyVersion` (sem chave no banco)
- [x] Idempotência por `(tenant_id, idempotency_key)`
- [x] Verificação detecta `BROKEN_CHAIN` e `SEQUENCE_GAP`
- [x] Eventos críticos (via OperationalEventLog) entram no ledger
- [x] `transactionLedgerEvidence` entra no Evidence Bundle
- [x] Auditoria sanitizada existe
- [x] `mvn -q -DskipTests compile` passa
- [x] `mvn test` passa

