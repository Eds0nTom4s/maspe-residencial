# Prompt 39.2 — Capability explícita e política de permissão para identificação assistida

## Objetivo
Separar explicitamente permissões sensíveis de **identificação assistida** (telefone/OTP/vinculação) das permissões gerais de vendas/pagamentos, evitando que qualquer POS/KDS/Quiosque “ganhe” acesso por herança implícita.

## Por que isso é sensível
Identificação assistida permite correlacionar um consumo a um telefone (mesmo mascarado), o que tem impacto de privacidade e risco operacional (ex.: device inadequado tentar vincular sessão indevida).

## Capabilities introduzidas (enum `DeviceCapability`)
- `LOOKUP_CONSUMPTION_BY_PHONE`
- `REQUEST_ASSISTED_IDENTIFICATION_OTP`
- `VERIFY_ASSISTED_IDENTIFICATION_OTP`
- `LINK_CUSTOMER_TO_SESSION`
- `CROSS_UNIT_ASSISTED_IDENTIFICATION` (bloqueada por default)

## Defaults por tipo (OperationalDeviceType)
Aplicados via bootstrap idempotente (primeiro uso/consulta):
- `POS_CAIXA`:
  - habilita lookup + request + verify + link
- `POS_ATENDIMENTO`:
  - habilita lookup + request (verify/link ficam desabilitados por default)
- `POS_QUIOSQUE` e `KDS_*`:
  - não recebem capabilities de identificação assistida
- `CROSS_UNIT_ASSISTED_IDENTIFICATION`:
  - nunca é aplicada por default (apenas manual/admin)

## Persistência (tenant-aware)
Tabela: `device_operational_capabilities`
- `tenant_id`, `dispositivo_operacional_id`, `capability`, `enabled`, `source`, timestamps.
- Permite habilitar/desabilitar capability por device sem alterar os demais eixos (payment policy templates/rollouts).

## Enforcement nos endpoints device
- `GET /device/sessoes-consumo/por-telefone`
  - exige `LOOKUP_CONSUMPTION_BY_PHONE`
- `POST /device/sessoes-consumo/{sessaoId}/identificacao/otp/request`
  - exige `REQUEST_ASSISTED_IDENTIFICATION_OTP`
- `POST /device/sessoes-consumo/{sessaoId}/identificacao/otp/verify`
  - exige `VERIFY_ASSISTED_IDENTIFICATION_OTP` + `LINK_CUSTOMER_TO_SESSION`
- Cross-unit:
  - se sessão pertence a outra unidade, exige `CROSS_UNIT_ASSISTED_IDENTIFICATION`.

## Configuração admin por device
Base: `/tenant/devices/{deviceId}/capabilities`
- `GET` lista capabilities do device (read-only para perfis permitidos)
- `PUT /{capability}` habilita/desabilita capability individual

## Auditoria (sanitizada)
Eventos:
- `DEVICE_CAPABILITY_DEFAULTS_BOOTSTRAPPED`
- `DEVICE_CAPABILITY_UPDATED`
- `ASSISTED_IDENTIFICATION_PERMISSION_DENIED`
- `ASSISTED_IDENTIFICATION_CROSS_UNIT_DENIED`

Regras:
- nunca registra OTP/otpHash/deviceToken;
- registra `tenantId`, `deviceId`, `unidadeId`, `capability`, `reason`.

## Limitações
- Não há templates/rollout para capabilities (fica para fase futura).
- Não há UI.

## Próximo passo recomendado
Prompt 39.3: templates de capabilities por OperationalDeviceType + rollout por unidade (similar ao Prompt 38.3, mas para segurança operacional).

