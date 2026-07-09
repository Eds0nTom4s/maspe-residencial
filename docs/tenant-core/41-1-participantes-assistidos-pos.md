# Prompt 41.1 — Participantes assistidos no POS

## Objetivo

Permitir que um **POS/device** faça a gestão assistida de participantes de uma `SessaoConsumo` (sessão compartilhada), com **capabilities explícitas**, **tenant-safety**, **unidade-safety**, **auditoria** e **privacidade**.

## Diferenças de fluxo

### Entrada pública por QR (Prompt 41)

- Origem: `QR_PUBLICO`
- Endpoints:
  - `POST /public/q/{token}/participantes/join/request`
  - `POST /public/q/{token}/participantes/join/verify`
  - `GET /public/q/{token}/participantes`
- Purpose OTP: `ENTRAR_SESSAO_COMPARTILHADA`

### Entrada assistida pelo POS (Prompt 41.1)

- Origem: `DEVICE_POS`
- Endpoints:
  - `GET /device/sessoes-consumo/{sessaoId}/participantes`
  - `POST /device/sessoes-consumo/{sessaoId}/participantes/otp/request`
  - `POST /device/sessoes-consumo/{sessaoId}/participantes/otp/verify`
- Purpose OTP: `POS_ADICIONAR_PARTICIPANTE_SESSAO`

## Capabilities

Novas `DeviceCapability` (sensíveis):

- `VIEW_SESSION_PARTICIPANTS`
- `ADD_SESSION_PARTICIPANT`
- `REMOVE_SESSION_PARTICIPANT`
- `PROMOTE_SESSION_PARTICIPANT`
- `DEMOTE_SESSION_PARTICIPANT`
- `BLOCK_SESSION_PARTICIPANT`
- `RESTORE_SESSION_PARTICIPANT`
- `MANAGE_SESSION_PARTICIPANTS` (agregada, reservada)

## Defaults por tipo de device

MVP (defaults/template bootstrap):

- `POS_CAIXA`:
  - `VIEW_SESSION_PARTICIPANTS`
  - `ADD_SESSION_PARTICIPANT`
  - `REMOVE_SESSION_PARTICIPANT`
- `POS_ATENDIMENTO`:
  - `VIEW_SESSION_PARTICIPANTS`
  - `ADD_SESSION_PARTICIPANT`
- `KDS_*`, `POS_QUIOSQUE`, `DISPLAY_SENHA`:
  - não recebem capabilities de gestão por default

## Como o POS adiciona participante (OTP)

1. POS chama `POST /device/.../participantes/otp/request` com `telefone` (e `nomeExibicao` opcional).
2. Backend cria/reutiliza `TelefoneOtpChallenge`:
   - tenant-safe
   - rate-limit reaproveitado
   - purpose `POS_ADICIONAR_PARTICIPANTE_SESSAO`
   - challenge vinculado à `SessaoConsumo`
3. Cliente dita o OTP ao operador.
4. POS chama `POST /device/.../participantes/otp/verify`.
5. Backend:
   - valida OTP e purpose
   - valida que o challenge pertence à sessão
   - cria/reutiliza `ClienteConsumo`
   - cria/reativa `SessaoConsumoParticipante` como `MEMBER` + `ACTIVE`
   - consome o challenge apenas após persistir a vinculação

## Remoção / promoção / rebaixamento / bloqueio / restauração

Endpoints device:

- `POST /device/.../participantes/{participanteId}/remove`
- `POST /device/.../participantes/{participanteId}/promote`
- `POST /device/.../participantes/{participanteId}/demote`
- `POST /device/.../participantes/{participanteId}/block`
- `POST /device/.../participantes/{participanteId}/restore`

Regras:

- Sessão precisa estar `ABERTA`
- Participante precisa pertencer à mesma sessão e tenant
- Device precisa estar na mesma unidade da sessão
- `reason` é obrigatório (sanitizado e limitado a 255)
- Proteção de OWNER:
  - não remover/rebaixar/bloquear o **último OWNER ACTIVE**
- Restauração:
  - permite restaurar `BLOCKED`/`REMOVED` para `ACTIVE`
  - volta como `MEMBER` no MVP

## Regra de role para criar pedido

Para `Pedido` atribuído a participante:

- `OWNER` pode pedir
- `MEMBER` pode pedir
- `GUEST` não pode pedir (MVP)
- `BLOCKED/REMOVED/LEFT` não podem pedir (via `status != ACTIVE`)

## Auditoria

Eventos relevantes (sanitizados):

- `SESSAO_PARTICIPANTE_POS_JOIN_REQUESTED`
- `SESSAO_PARTICIPANTE_POS_JOIN_VERIFIED`
- `SESSAO_PARTICIPANTE_REMOVED_BY_POS`
- `SESSAO_PARTICIPANTE_PROMOTED_BY_POS`
- `SESSAO_PARTICIPANTE_DEMOTED_BY_POS`
- `SESSAO_PARTICIPANTE_BLOCKED_BY_POS`
- `SESSAO_PARTICIPANTE_RESTORED_BY_POS`
- `SESSAO_PARTICIPANTE_PERMISSION_DENIED`

Não incluir:

- OTP
- `otpHash`
- telefone completo
- `deviceToken`

## Limitações

- Não implementa split de pagamento (pagamento continua por `SessaoConsumo`)
- Não implementa participantes offline
- Não implementa aprovação remota do OWNER
- Não implementa UI

## Próximo passo recomendado

Prompt 41.2: capability/cross-unit explícita para gestão de participantes (caso o produto permita POS multi-unidade) + endpoints admin para revisão/auditoria por sessão.

