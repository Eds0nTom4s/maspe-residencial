# Prompt 41.2 — Convite/aprovação de participantes pelo OWNER + políticas de entrada

## Objetivo

Adicionar governança mínima de entrada em `SessaoConsumo` compartilhada:

- política de entrada por sessão;
- participantes `PENDING_APPROVAL`;
- aprovação/rejeição pelo **OWNER** (sem JWT permanente de cliente);
- aprovação/rejeição pelo **POS** com capability;
- convite por telefone (OWNER/POS) + aceitação por OTP.

Pagamento permanece no nível da `SessaoConsumo` (sem split).

## Política de entrada (`SessaoParticipantEntryPolicy`)

Campo em `sessoes_consumo.participant_entry_policy` (default `OTP_AUTO_JOIN`).

- `OTP_AUTO_JOIN`: join por QR + OTP entra como `ACTIVE` (comportamento do Prompt 41).
- `OTP_REQUIRES_OWNER_APPROVAL`: join por QR + OTP cria participante `PENDING_APPROVAL`.
- `INVITE_ONLY`: join público livre bloqueado; entrada via convite + OTP (`ACEITAR_CONVITE_SESSAO`).
- `POS_ONLY`: join público bloqueado; somente POS adiciona/invita participantes.

## Status de participante

Novos status em `SessaoParticipanteStatus`:

- `PENDING_APPROVAL`: OTP validado, aguardando decisão.
- `EXPIRED`: convite expirado (quando aplicável).

Regras:

- Apenas `ACTIVE` pode pedir.
- `INVITED` / `PENDING_APPROVAL` / `REJECTED` não podem pedir.
- `GUEST` não pode pedir (MVP).

## Fluxos públicos (QR)

### Join por QR + OTP

Endpoints existentes:

- `POST /public/q/{token}/participantes/join/request`
- `POST /public/q/{token}/participantes/join/verify`

Agora respeitam `participant_entry_policy`:

- bloqueiam em `INVITE_ONLY` e `POS_ONLY`;
- criam `PENDING_APPROVAL` em `OTP_REQUIRES_OWNER_APPROVAL`.

### Auth do OWNER por OTP

Sem JWT permanente: o OWNER se autentica no momento da decisão via OTP do seu telefone.

- `POST /public/q/{token}/owner-auth/otp/request`

### Aprovação/rejeição pelo OWNER

- `POST /public/q/{token}/participantes/pending` (lista pendentes; exige OTP do OWNER)
- `POST /public/q/{token}/participantes/{participanteId}/approve`
- `POST /public/q/{token}/participantes/{participanteId}/reject`

### Convite pelo OWNER + aceitação por OTP

- `POST /public/q/{token}/participantes/invite` (exige OTP do OWNER; envia OTP ao convidado)
- `POST /public/q/{token}/participantes/invite/accept`

OTP do convidado usa `OtpPurpose.ACEITAR_CONVITE_SESSAO` e é tenant-safe + session-bound.

## Fluxos device/POS

Controller base:

`/device/sessoes-consumo/{sessaoId}/participantes`

Novos endpoints:

- `GET /pending` (capability `VIEW_PENDING_SESSION_PARTICIPANTS`)
- `POST /{participanteId}/approve` (capability `APPROVE_SESSION_PARTICIPANT`)
- `POST /{participanteId}/reject` (capability `REJECT_SESSION_PARTICIPANT`)
- `PUT /participant-entry-policy` (capability `CHANGE_SESSION_ENTRY_POLICY`)
- `POST /invite` (capability `INVITE_SESSION_PARTICIPANT`)

## Capabilities

Novas capabilities (`DeviceCapability`):

- `VIEW_PENDING_SESSION_PARTICIPANTS`
- `APPROVE_SESSION_PARTICIPANT`
- `REJECT_SESSION_PARTICIPANT`
- `CHANGE_SESSION_ENTRY_POLICY`
- `INVITE_SESSION_PARTICIPANT`

Defaults (MVP):

- `POS_CAIXA`: `VIEW_PENDING` + `APPROVE` + `REJECT` + `INVITE`
- `POS_ATENDIMENTO`: `VIEW_PENDING` + `INVITE`
- `CHANGE_SESSION_ENTRY_POLICY`: não habilitado por default

## Auditoria e privacidade

Eventos (sanitizados):

- `SESSAO_ENTRY_POLICY_CHANGED`
- `SESSAO_PARTICIPANTE_JOIN_PENDING_APPROVAL`
- `SESSAO_PARTICIPANTE_APPROVED_BY_OWNER` / `REJECTED_BY_OWNER`
- `SESSAO_PARTICIPANTE_APPROVED_BY_POS` / `REJECTED_BY_POS`
- `SESSAO_PARTICIPANTE_INVITED_BY_OWNER` / `INVITED_BY_POS`
- `SESSAO_PARTICIPANTE_INVITE_ACCEPTED`
- `SESSAO_OWNER_AUTH_OTP_REQUESTED` / `VERIFIED`

Nunca registrar em auditoria:

- OTP
- `otpHash`
- telefone completo
- `deviceToken`

## Limitações

- Sem split de pagamento.
- Sem pagamento por participante.
- Sem app mobile/JWT permanente para clientes.
- Sem push/chat/WhatsApp.
- Sem participantes offline/OTP offline.

## Próximo passo recomendado

41.3: token curto de autorização do OWNER (TTL) para reduzir fricção (sem JWT permanente), + política por unidade/device para permitir/bloquear `CHANGE_SESSION_ENTRY_POLICY`.

