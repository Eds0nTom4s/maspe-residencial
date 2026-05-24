# CONSUMA — 41.5 Ações de Gestão via ownerActionToken

> **Módulo:** Sessão Compartilhada — UX Hardening Operacional  
> **Sprint:** 41.5  
> **Status:** ✅ Implementado e Testado (42/42 testes)

---

## Visão Geral

O `ownerActionToken` é um token curto de ação pós-OTP que permite ao **OWNER** da sessão
aprovar, rejeitar, cancelar e reenviar convites de participantes **sem redigitar o OTP** a cada
operação, dentro do TTL e maxUses configurados.

### Fluxo de vida do Token

```
[OWNER solicita OTP]
    → OTP verificado (Prompt 41.2)
    → ownerActionToken emitido (Prompt 41.4)
         TTL: 10 min | maxUses: 20
    → OWNER realiza ações (este prompt)
    → Token expira por TTL, maxUses ou fechar a sessão
    → Job de cleanup remove tokens finalizados (Prompt 41.5)
```

---

## Endpoints

**Base:** `POST /public/q/{qrToken}/participantes/{participanteId}/**`

O token é extraído por precedência:
1. **Header** `X-Owner-Action-Token` ← _preferencial_
2. Campo `ownerActionToken` no body JSON
3. **Query param** → **NUNCA aceito** (lança `OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED`)

### Aprovar participante

```
POST /public/q/{qrToken}/participantes/{participanteId}/approve-by-token

Header: X-Owner-Action-Token: <rawToken>
Body: { "reason": "opcional" }

Resposta 200:
{
  "participanteId": 123,
  "status": "ACTIVE",
  "approved": true,
  "approvedAt": "2026-05-24T...",
  "ownerParticipanteId": 10
}
```

**Pré-condições:** participante em `PENDING_APPROVAL`, não expirado, token ACTIVE/válido.

### Rejeitar participante

```
POST /public/q/{qrToken}/participantes/{participanteId}/reject-by-token

Header: X-Owner-Action-Token: <rawToken>
Body: { "reason": "opcional" }

Resposta 200: { "participanteId", "status": "REJECTED", "rejected": true, ... }
```

### Cancelar convite/pendência

```
POST /public/q/{qrToken}/participantes/{participanteId}/cancel-by-token

Header: X-Owner-Action-Token: <rawToken>
Body: { "reason": "opcional" }

Resposta 200: { "participanteId", "status": "CANCELLED", "wasCancelled": true/false, ... }
```

**Idempotente:** Se já `CANCELLED`, retorna `wasCancelled: false` sem re-auditar.

Estados canceláveis: `INVITED`, `PENDING_OTP`, `PENDING_APPROVAL`.

### Reenviar convite

```
POST /public/q/{qrToken}/participantes/{participanteId}/resend-invite-by-token

Header: X-Owner-Action-Token: <rawToken>
Body: {}  (sem campos obrigatórios)

Resposta 200:
{
  "participanteId": 123,
  "status": "INVITED",
  "resent": true,
  "resendCount": 2,
  "lastResendAt": "...",
  "canResend": false
}
```

Reutiliza lógica de cooldown e maxResends do Prompt 41.3.

---

## Erros de Negócio

| Código | Situação |
|--------|----------|
| `OWNER_ACTION_TOKEN_REQUIRED` | Token ausente/blank na request |
| `OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED` | Token enviado em query param |
| `OWNER_ACTION_TOKEN_INVALID` | Token inexistente ou de outro tenant |
| `OWNER_ACTION_TOKEN_EXPIRED` | Token expirado por TTL |
| `OWNER_ACTION_TOKEN_EXHAUSTED` | Token esgotou os maxUses |
| `OWNER_ACTION_TOKEN_REVOKED` | Token revogado (ex: sessão fechada) |
| `OWNER_ACTION_TOKEN_SESSION_MISMATCH` | Token não pertence à sessão |
| `OWNER_ACTION_TOKEN_OWNER_NOT_ACTIVE` | OWNER mudou de status |
| `OWNER_ACTION_TOKEN_OWNER_NOT_OWNER` | OWNER foi rebaixado de role |
| `PARTICIPANT_ALREADY_ACTIVE` | Participante já está ACTIVE (aprovar) |
| `PARTICIPANTE_NOT_PENDING_APPROVAL` | Estado incorreto para aprovar |
| `PARTICIPANTE_NOT_REJECTABLE` | Estado incorreto para rejeitar |
| `PARTICIPANTE_NOT_CANCELABLE` | Estado incorreto para cancelar |
| `PARTICIPANT_REQUEST_EXPIRED` | Pedido de pendência expirou |
| `PARTICIPANTE_INVITE_NOT_RESENDABLE` | Convite não pode ser reenviado (cooldown ou maxResends) |

---

## Revogação ao Fechar Sessão

Ao fechar uma `SessaoConsumo`, todos os tokens ACTIVE dessa sessão são revogados em lote:

```java
ownerTokenService.revokeActiveTokensBySessao(tenantId, sessaoId, "SESSION_CLOSE", ip, ua);
```

- Operação idempotente
- PESSIMISTIC_WRITE para evitar race conditions
- Auditoria agregada (`SESSAO_OWNER_ACTION_TOKENS_REVOKED_BY_SESSION_CLOSE`) sem expor tokenHash

---

## Job de Limpeza

**Classe:** `SessaoOwnerActionTokenCleanupJob`  
**Schedule:** `0 20 3 * * *` (03:20 diário, configurável)

Remove fisicamente tokens com status `EXPIRED`, `CONSUMED` ou `REVOKED` com `createdAt` anterior
ao período de retenção (default: 30 dias). O DELETE físico é seguro pois o raw token nunca foi
armazenado.

### Configuração

```properties
consuma.sessao.owner-action-token.cleanup.enabled=${OWNER_TOKEN_CLEANUP_ENABLED:true}
consuma.sessao.owner-action-token.cleanup.retention-days=${OWNER_TOKEN_CLEANUP_RETENTION_DAYS:30}
consuma.sessao.owner-action-token.cleanup.batch-size=${OWNER_TOKEN_CLEANUP_BATCH_SIZE:500}
consuma.sessao.owner-action-token.cleanup.cron=${OWNER_TOKEN_CLEANUP_CRON:0 20 3 * * *}
```

---

## Validação do Pepper em Produção

**Classe:** `SessaoOwnerActionTokenPepperValidator`

Ao iniciar a aplicação com profile `prod`, `production` ou `homolog`, valida que
`consuma.sessao.owner-action-token.hash-pepper` está configurado.

- Falha em startup com mensagem clara (`OWNER_ACTION_TOKEN_PEPPER_REQUIRED_IN_PRODUCTION`)
- **Nunca imprime o valor do pepper** nos logs
- Em perfis `dev`/`test`, pepper vazio é aceito

**Variável de ambiente (produção):**

```bash
CONSUMA_OWNER_ACTION_TOKEN_PEPPER=<valor-forte-aleatório>
```

---

## Auditoria

Todos os eventos são registados em `OperationalEventLog` com metadados sanitizados:
- **Nunca** contêm `rawToken`, `tokenHash` ou valores confidenciais
- Registam: `tenantId`, `sessaoConsumoId`, `participanteId`, `ownerParticipanteId`, `oldStatus`, `newStatus`

| Evento | Situação |
|--------|----------|
| `SESSAO_PARTICIPANTE_APPROVED_BY_OWNER_TOKEN` | Aprovação via token |
| `SESSAO_PARTICIPANTE_REJECTED_BY_OWNER_TOKEN` | Rejeição via token |
| `SESSAO_PARTICIPANTE_CANCELLED_BY_OWNER_TOKEN` | Cancelamento via token |
| `SESSAO_PARTICIPANTE_INVITE_RESENT_BY_OWNER_TOKEN` | Reenvio via token |
| `SESSAO_OWNER_ACTION_TOKENS_REVOKED_BY_SESSION_CLOSE` | Revogação em massa ao fechar sessão |
| `SESSAO_OWNER_ACTION_TOKEN_CLEANUP_RUN` | Execução do job de cleanup |
| `SESSAO_OWNER_ACTION_TOKEN_PEPPER_VALIDATION_FAILED` | Pepper ausente em produção |

---

## Componentes Criados

| Artefacto | Tipo | Responsabilidade |
|-----------|------|------------------|
| `SessaoParticipanteOwnerTokenActionService` | Service | Orquestração de ações via token |
| `OwnerActionTokenExtractor` | Component | Extracção segura do token da request |
| `PublicOwnerTokenActionController` | Controller | Endpoints REST das 4 ações |
| `SessaoOwnerActionTokenCleanupJob` | Job | Cleanup físico de tokens finalizados |
| `SessaoOwnerActionTokenPepperValidator` | Component | Validação de startup do pepper |
| `SessaoOwnerActionTokenProperties.Cleanup` | Config | Sub-properties do cleanup job |

## Testes (42 passam)

| Suíte | Testes |
|-------|--------|
| `SessaoParticipanteOwnerTokenActionServiceTest` | 16 |
| `OwnerActionTokenExtractorTest` | 8 |
| `SessaoOwnerActionTokenRevocationTest` | 5 |
| `SessaoOwnerActionTokenCleanupJobTest` | 6 |
| `SessaoOwnerActionTokenPepperValidationTest` | 7 |
| **Total** | **42** |
