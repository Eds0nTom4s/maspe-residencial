# Prompt 41 — Sessão Compartilhada com Múltiplos Participantes

## Objetivo

Permitir que uma `SessaoConsumo` tenha **múltiplos participantes** (pessoas) identificados por `ClienteConsumo`, sem quebrar:

- consumo anónimo;
- pagamentos no nível da sessão (MVP);
- offline sync / replay;
- AppyPay/OTP (online-only).

## Conceitos

### `SessaoConsumo`
Representa o **contexto** de consumo (mesa/unidade/QR) e o ciclo de vida do consumo.

### `ClienteConsumo`
Representa uma pessoa identificada por telefone verificado via OTP (não é `TenantUser`).

### `SessaoConsumoParticipante`
Representa a relação “pessoa ↔ sessão”:

`SessaoConsumo` → `SessaoConsumoParticipante` → `ClienteConsumo`

Isso evita “múltiplos telefones” diretamente na sessão.

## Roles e status

### `SessaoParticipanteRole`
- `OWNER`: participante principal (quando aplicável)
- `MEMBER`: participante que entrou via telefone+OTP
- `GUEST`: reservado para futuro

### `SessaoParticipanteStatus`
- `PENDING_OTP`: reservado para futuro (neste prompt, join vira `ACTIVE` no verify)
- `ACTIVE`: pode criar pedido
- `LEFT/REMOVED/BLOCKED/...`: reservado para evoluções

## Fluxo público (QR operacional)

Base: `/public/q/{token}/participantes`

### 1) Join request (gera OTP)
`POST /join/request`

- resolve tenant/unidade/sessão via QR operacional;
- cria OTP com `OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA`;
- **não ativa participante** ainda.

### 2) Join verify (valida OTP e ativa participante)
`POST /join/verify`

- valida OTP (tenant-safe, phone-safe, session-safe);
- cria/reutiliza `ClienteConsumo` e marca telefone como verificado;
- cria/reutiliza `SessaoConsumoParticipante` e marca `ACTIVE`;
- role:
  - se sessão já possui `clienteConsumo` principal: esse vira `OWNER` (se não existir participante ainda) e o joiner vira `MEMBER`;
  - se sessão não possui principal: o primeiro joiner vira `OWNER` e a sessão passa a ter `clienteConsumo` principal (sem alterar regras financeiras).

### 3) Listagem mínima (sem telefone)
`GET /public/q/{token}/participantes`

Retorna apenas:
- `participanteId`, `nomeExibicao`, `role`, `status`, `joinedAt`

Sem telefone completo / sem dados sensíveis.

## Pedido atribuído a participante

O pedido público continua funcionando sem participante.

Quando o request inclui `participanteId`:
- valida que o participante está `ACTIVE` e pertence à mesma sessão;
- grava em `Pedido`:
  - `sessao_participante_id`
  - `cliente_consumo_id`

Pagamento continua no nível da sessão (sem split).

## Endpoint device (listagem)

Base: `/device/sessoes-consumo/{sessaoId}/participantes`

Retorna resumo operacional (tenant-safe e unidade-safe).

## Auditoria

Eventos adicionados:
- `SESSAO_PARTICIPANTE_JOIN_REQUESTED`
- `SESSAO_PARTICIPANTE_JOIN_VERIFIED`
- `SESSAO_PARTICIPANTE_ADDED` (bootstrap do OWNER quando sessão já tinha `clienteConsumo`)
- `PEDIDO_ATRIBUIDO_PARTICIPANTE`

Sanitização:
- nunca inclui OTP/otpHash
- não expõe telefone completo
- não expõe deviceToken

## Limitações (intencionais)

- Não implementa split de pagamento por participante.
- Não implementa aprovação complexa do OWNER.
- Não implementa leave seguro avançado no público.
- Não implementa gestão completa de participantes via POS/admin.
- Não implementa OTP offline / AppyPay offline.

## Dívida técnica registrada (não implementar aqui)

Prompt 40.5 — Cancelamento controlado de replay operations (PENDING cancelável; RUNNING com cancelRequested; liberar `replay_in_progress` com segurança).

## Próximo passo recomendado

- Prompt 41.1: fluxo assistido no POS para adicionar/remover participantes (com capability explícita e auditoria).

