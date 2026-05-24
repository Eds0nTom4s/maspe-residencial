# Prompt 41.3 — Convites, expiração e limpeza operacional de participantes pendentes

## Objetivo

Dar ciclo de vida operacional a participantes temporários de sessão compartilhada:

- TTL para `INVITED` e `PENDING_APPROVAL`;
- cancelamento explícito (OWNER / POS);
- reenvio controlado de convite (cooldown + maxResends);
- job agendado para expirar pendências automaticamente;
- auditoria sanitizada.

## Modelo de dados

Migration:

- `V44__sessao_participante_expiracao_limpeza.sql`

Campos adicionados em `sessao_consumo_participantes`:

- `expires_at`, `expired_at`, `expiration_reason`, `cleanup_batch_id`
- `cancelled_at`, `cancelled_by_participante_id`, `cancelled_by_device_id`, `cancellation_reason`
- `resend_count`, `last_resend_at`, `last_reminder_at`

Status adicional:

- `SessaoParticipanteStatus.CANCELLED`

## TTL

- `INVITED`: `expiresAt = invitedAt + inviteTtlMinutes`
- `PENDING_APPROVAL`: `expiresAt = approvalRequestedAt + pendingApprovalTtlMinutes`

Regra:

- `ACTIVE`, `REJECTED`, `REMOVED`, `LEFT`, `BLOCKED`, `CANCELLED` não expiram automaticamente.

## Reenvio de convite

Regras:

- Apenas para `INVITED` (ou `PENDING_OTP` se usado).
- `cooldown`: bloqueia antes de `last_resend_at + resendCooldownSeconds`
- `maxResends`: bloqueia após `resend_count >= maxResends`
- Ao reenviar:
  - envia OTP (`ACEITAR_CONVITE_SESSAO`)
  - incrementa `resend_count`
  - atualiza `last_resend_at`
  - renova `expires_at` (novo TTL de convite)

## Cancelamento

Pode cancelar:

- `INVITED`
- `PENDING_APPROVAL`
- `PENDING_OTP`

Cancelamento:

- muda `status` para `CANCELLED`
- preenche `cancelled_at`
- grava `cancelled_by_participante_id` (OWNER) ou `cancelled_by_device_id` (POS)

## Job de expiração

Job:

- `SessaoParticipanteExpirationJob`

Service:

- `SessaoParticipanteExpirationService`

Regras:

- busca candidatos (`INVITED`, `PENDING_OTP`, `PENDING_APPROVAL`) com `expires_at < now`
- marca como `EXPIRED`, grava `expired_at`, `expiration_reason`, `cleanup_batch_id`
- não apaga fisicamente (limpeza lógica)

## Endpoints

### Público (OWNER)

OWNER continua provando posse por OTP (Prompt 41.2). Novos endpoints:

- `POST /public/q/{token}/participantes/{participanteId}/cancel`
- `POST /public/q/{token}/participantes/{participanteId}/resend-invite`

### Device/POS

Novos endpoints:

- `POST /device/sessoes-consumo/{sessaoId}/participantes/{participanteId}/cancel`
- `POST /device/sessoes-consumo/{sessaoId}/participantes/{participanteId}/resend-invite`
- `POST /device/sessoes-consumo/{sessaoId}/participantes/{participanteId}/expire` (manual)

## Capabilities (POS)

Novas capabilities:

- `CANCEL_SESSION_PARTICIPANT_INVITE`
- `RESEND_SESSION_PARTICIPANT_INVITE`
- `EXPIRE_SESSION_PARTICIPANT_MANUALLY`

Defaults (MVP):

- `POS_CAIXA`: cancel + resend
- `POS_ATENDIMENTO`: resend

## Auditoria

Eventos (sanitizados):

- `SESSAO_PARTICIPANTE_INVITE_RESENT_BY_OWNER`
- `SESSAO_PARTICIPANTE_INVITE_RESENT_BY_POS`
- `SESSAO_PARTICIPANTE_CANCELLED_BY_OWNER`
- `SESSAO_PARTICIPANTE_CANCELLED_BY_POS`
- `SESSAO_PARTICIPANTE_EXPIRED_BY_JOB`
- `SESSAO_PARTICIPANTE_EXPIRED_MANUALLY_BY_POS`
- `SESSAO_PARTICIPANTE_RESEND_BLOCKED`

Não registrar:

- OTP / `otpHash`
- telefone completo
- `deviceToken`

## Limitações

- Não implementa push/WhatsApp/app mobile.
- Não implementa split/pagamento por participante.
- Não apaga fisicamente registros (apenas status e timestamps).

## Próximo passo recomendado

41.4: UX hardening (ex.: token curto do OWNER pós-OTP para múltiplas ações sem repetir OTP) + endpoints de listagem “pendências gerais” incluindo `INVITED`/`PENDING_APPROVAL` com filtros.

