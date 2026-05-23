# Prompt 39.1 — OTP assistido no POS para vincular SessaoConsumo

## Objetivo
Permitir que o operador no POS/device auxilie um cliente presente a **vincular uma SessaoConsumo específica** ao seu telefone, mantendo prova de posse via OTP.

## Diferença entre fluxos
- **Identificação pública via QR** (Prompt 39): o próprio cliente solicita/valida OTP no contexto do QR.
- **Recuperação pública via QR** (Prompt 39): OTP para listar sessões ativas por telefone no contexto do QR.
- **OTP assistido no POS** (Prompt 39.1): o POS inicia o OTP para uma `SessaoConsumo` específica; o cliente dita o código; o POS valida e vincula.

## Endpoints device/POS
Base: `/device/sessoes-consumo/{sessaoId}/identificacao`

- `POST /otp/request`
  - cria/reutiliza challenge `PENDING` com `purpose=POS_VINCULAR_SESSAO` vinculado à sessão;
  - respeita rate limit e resend cooldown/maxResends;
  - telefone é normalizado e mascarado;
  - `debugOtp` só aparece em mock/test.

- `POST /otp/verify`
  - valida OTP **sem consumir** inicialmente;
  - faz lock pessimista da `SessaoConsumo` para impedir race condition;
  - vincula `ClienteConsumo` e marca sessão como `IDENTIFICADA`;
  - só então consome o challenge (`CONSUMED`).

## Regras de segurança
- Tenant vem do `DevicePrincipal`.
- Por padrão, o device só vincula sessão da **mesma unidade** (`SESSAO_CONSUMO_UNIDADE_DIFERENTE_DEVICE`).
- Sessão deve estar `ABERTA`.
- Sessão já identificada com telefone diferente retorna erro (`SESSAO_CONSUMO_ALREADY_IDENTIFIED`).
- Sessão já identificada com o mesmo telefone retorna 200 idempotente (`statusMensagem=JA_IDENTIFICADA`).

## Transação e consistência
- OTP não é consumido antes da vinculação da sessão.
- Vinculação e consumo do challenge ocorrem na mesma transação.
- Lock pessimista evita que duas verificações concorrentes vinculem clientes diferentes.

## Auditoria (sanitizada)
- Eventos do fluxo assistido:
  - `SESSAO_CONSUMO_IDENTIFICACAO_POS_REQUESTED`
  - `SESSAO_CONSUMO_IDENTIFICACAO_POS_VERIFIED`
  - `SESSAO_CONSUMO_IDENTIFICACAO_POS_FAILED`
- Telefone sempre mascarado; nunca grava OTP/otpHash/deviceToken.

## Limitações
- Não é login completo de cliente (sem password/JWT permanente).
- Não implementa POS offline.

## Próximo passo recomendado
- Prompt 39.2: políticas/capabilities mais específicas para “identificação assistida” (separar capability dedicada).

