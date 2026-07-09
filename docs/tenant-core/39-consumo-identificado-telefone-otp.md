# Prompt 39 — Consumo identificado com telefone + OTP

## Objetivo
Adicionar uma camada **opcional** de identificação leve por telefone + OTP para:
- vincular uma `SessaoConsumo` anónima a um telefone verificado;
- permitir recuperação de sessões ativas por telefone (no contexto seguro do QR);
- permitir que o POS/device consulte sessões ativas por telefone (operação presencial).

Não é um “login de cliente” completo: não há password, nem JWT permanente de cliente.

## Conceitos
- **ClienteConsumo**: perfil leve por tenant, identificado por `telefone_normalizado`.
- **TelefoneOtpChallenge**: desafio OTP com hash, expiração, tentativas e reenvio controlado.
- **SessaoConsumo identificada**: `cliente_consumo_id` + `telefone_identificado` + `identificacao_status=IDENTIFICADA`.

## Privacidade
- OTP nunca é persistido em texto claro (apenas `otp_hash`).
- Responses e auditoria usam **telefone mascarado**.
- `debugOtp` só é retornado quando `consuma.otp.mock-enabled=true` (ambiente de teste/mock).

## Normalização de telefone (Angola)
Aceita:
- `9XXXXXXXX`
- `2449XXXXXXXX`
- `+2449XXXXXXXX`

Normaliza para:
- `+2449XXXXXXXX`

Erros: `PHONE_INVALID`.

## OTP: geração, envio e validação
Configurações principais (`consuma.otp.*`):
- `enabled`
- `length`
- `ttl-minutes`
- `max-attempts`
- `resend-cooldown-seconds`
- `max-resends`
- `max-active-challenges-per-phone`
- `hash-pepper`
- `mock-enabled`

Hash:
- HMAC-SHA256 com `hash-pepper`
- mensagem: `challengeId:otp`

Reenvio:
- cooldown por challenge;
- limite de resends.

Rate limit (MVP em banco):
- por tenant+telefone (última hora);
- por tenant+IP (última hora).

## Endpoints públicos (QR)
Base: `/public/q/{token}`

Identificação (vincular sessão):
- `POST /identificacao/otp/request`
- `POST /identificacao/otp/verify`

Recuperação de sessão (contexto QR):
- `POST /recuperar/otp/request`
- `POST /recuperar/otp/verify`

Notas:
- O tenant é resolvido pelo `{token}` (tenant-safe).
- Recuperação **não** é “global” por tenantSlug nesta fase.

## Endpoints device/POS
- `GET /device/sessoes-consumo/por-telefone?telefone=...`

Regras:
- tenant/unidade vêm do `DevicePrincipal`;
- exige capability do device (`MANAGE_CONSUMPTION_FUND` ou `REPRINT_CONSUMPTION_DOCUMENTS`).

## Auditoria (sanitizada)
Eventos adicionados:
- `CLIENTE_CONSUMO_CREATED`
- `CLIENTE_CONSUMO_PHONE_VERIFIED`
- `SESSAO_CONSUMO_IDENTIFICADA`
- `OTP_CHALLENGE_CREATED`
- `OTP_CHALLENGE_VERIFIED`
- `OTP_CHALLENGE_FAILED`
- `OTP_CHALLENGE_EXPIRED`
- `OTP_RATE_LIMITED`
- `SESSAO_CONSUMO_RECUPERADA_POR_TELEFONE`

Entidades auditáveis:
- `CLIENTE_CONSUMO`
- `TELEFONE_OTP_CHALLENGE`

Metadados:
- `tenantId`, `unidadeId`, `sessaoConsumoId`, `clienteConsumoId`, `telefoneMascarado`, `purpose`, `attempts`.

Não registra:
- OTP em texto, `otp_hash`, payload SMS, secrets.

## Limitações
- Não implementa UI.
- Não implementa login completo do cliente (sem password/JWT permanente).
- Não implementa recuperação global fora do contexto QR.
- Não implementa WhatsApp/push/e-mail.

## Próximo passo recomendado
Prompt 39.1: “OTP assistido no POS” (fluxo device request/verify OTP vinculado a uma sessão específica).

