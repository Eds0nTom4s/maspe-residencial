# Prompt 37.7 — Hardening WORM: Trigger PostgreSQL anti-mutation + retenção lógica

## Objetivo
Reforçar a imutabilidade interna dos Evidence Bundles persistidos (WORM lógico), adicionando:

- **Trigger PostgreSQL** para bloquear `UPDATE/DELETE` destrutivos na tabela `turno_evidence_bundles`;
- **Job agendado de retenção lógica** para marcar bundles vencidos como `RETENTION_EXPIRED` (sem delete físico).

## WORM lógico por API vs WORM reforçado por banco
Antes desta fase, o WORM era garantido principalmente por desenho de API/service (sem endpoints de update/delete).

Nesta fase:
- O **banco passa a bloquear** mutações fora das transições permitidas de status, mesmo via SQL direto com o usuário normal da aplicação.

## Trigger PostgreSQL (anti-UPDATE/DELETE)
Migration: `src/main/resources/db/migration/V27__harden_turno_evidence_bundle_worm.sql`

### O que é bloqueado
- `DELETE` físico em `turno_evidence_bundles` é sempre bloqueado.
- `UPDATE` em campos críticos é bloqueado (ex: `bundle_json`, hashes, assinaturas, cadeia, tenant/turno, sequence).
- `retention_until` e `worm_locked` também são imutáveis após criação.

### O que é permitido
Somente **transições controladas de `status`**:
- `ACTIVE → RETENTION_EXPIRED`
- `ACTIVE → QUARANTINED`
- `SUPERSEDED → RETENTION_EXPIRED`
- `SUPERSEDED → QUARANTINED`

Campos “operacionais” permitidos junto com a transição:
- `updated_at`
- `modified_by`
- `version` (otimistic locking do JPA)

### O que NÃO é permitido
- Qualquer transição de volta para `ACTIVE`.
- Qualquer update “no-op” que altere `updated_at/version` sem mudança de `status`.

### Limitações
- Um **superuser** do PostgreSQL pode desabilitar/burlar triggers. Isso não substitui:
  - controle de acesso ao banco;
  - backups imutáveis;
  - trilhas de auditoria externas.

## Retenção lógica (job)
Objetivo: após `retentionUntil < now()`, marcar bundles como `RETENTION_EXPIRED`.

### Importante
`RETENTION_EXPIRED` significa:
- “fim do período mínimo de retenção”, **não** significa apagado.

Bundles expirados:
- continuam **consultáveis**;
- continuam **verificáveis** (hash/assinatura/cadeia permanecem).

### Configuração
Properties:
- `consuma.financeiro.evidence-bundle.retention-job-enabled` (default `true`)
- `consuma.financeiro.evidence-bundle.retention-job-cron` (default `0 0 3 * * *`)
- `consuma.financeiro.evidence-bundle.retention-job-batch-size` (default `100`)

Job:
- `EvidenceBundleRetentionJob` executa `EvidenceBundleRetentionService.runOnce("SCHEDULED_JOB")`.

Auditoria:
- Access log usa `EvidenceBundleAccessType.RETENTION_EXPIRED`.
- Evento operacional sanitizado: `OperationalEventType.EVIDENCE_BUNDLE_RETENTION_EXPIRED`.

## Por que não existe delete físico
Evidence Bundle persistido é evidência (append-only). O objetivo é reduzir risco de adulteração/remoção destrutiva e suportar auditoria operacional.

## Próximo passo recomendado
- Persistência externa WORM (ex: storage com Object Lock) e/ou trilha externa (WORM fora do banco), mantendo o bundle persistido interno como primeira camada.

