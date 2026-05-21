# Prompt 38 — Métodos de pagamento tenant-aware

## Objetivo
Permitir que cada **tenant** configure de forma **segura e auditável** quais métodos de pagamento estão:

- ativos/inativos;
- disponíveis por canal (QR público vs POS/device);
- disponíveis por destino (PEDIDO vs FUNDO_CONSUMO);
- com limites min/max e ordenação de exibição.

Princípio central: **AppyPay é opcional**. CASH/TPA devem permitir operação presencial sem gateway.

## Modelo
Tabela: `tenant_payment_methods`

Campos principais:
- `code`: `CASH | TPA | APPYPAY`
- `status`: `ACTIVE | INACTIVE | SUSPENDED`
- flags por canal/destino:
  - `enabled_for_qr`
  - `enabled_for_pos`
  - `enabled_for_pedido`
  - `enabled_for_fundo_consumo`
- regras do método:
  - `requires_open_turno`
  - `requires_gateway`
  - `requires_manual_confirmation`
- limites:
  - `min_amount`, `max_amount`, `currency`
- UX:
  - `display_name`, `description`, `sort_order`, `icon_key`

## Defaults / Bootstrap
Ao primeiro uso (lazy bootstrap), se o tenant não tiver métodos configurados:
- `CASH`: ACTIVE, QR/POS habilitados, PEDIDO/FUNDO habilitados, `requires_open_turno=true`.
- `TPA`: ACTIVE, QR/POS habilitados, PEDIDO/FUNDO habilitados, `requires_open_turno=true`.
- `APPYPAY`: ACTIVE se AppyPay estiver configurado no ambiente; caso contrário INACTIVE.

O bootstrap é idempotente.

## Endpoints

### Admin (tenant)
Base: `/tenant/payment-methods`

- `GET /tenant/payment-methods` (OWNER/ADMIN/FINANCE/CASHIER read-only)
- `GET /tenant/payment-methods/{code}` (OWNER/ADMIN/FINANCE/CASHIER read-only)
- `PATCH /tenant/payment-methods/{code}` (OWNER/ADMIN/FINANCE)
- `POST /tenant/payment-methods/{code}/activate` (OWNER/ADMIN/FINANCE)
- `POST /tenant/payment-methods/{code}/deactivate` (OWNER/ADMIN/FINANCE)

### Disponibilidade (frontend/POS)
- `GET /public/q/{token}/payment-methods?destination=PEDIDO|FUNDO_CONSUMO`
- `GET /device/payment-methods?destination=PEDIDO|FUNDO_CONSUMO`

## Integração com fluxos existentes

### CASH/TPA (ordens manuais)
- Ao criar ordem manual (QR público), o método precisa estar:
  - configurado e ACTIVE;
  - habilitado para QR;
  - habilitado para o destino (PEDIDO/FUNDO_CONSUMO);
  - dentro do min/max;
  - e, se `requires_open_turno=true`, exige turno aberto no momento da criação.

- Ao confirmar ordem no POS:
  - revalida método ACTIVE/habilitado para POS e destino.
  - Se método foi desativado após a criação da ordem, a confirmação é bloqueada.

### AppyPay (gateway)
- Ao iniciar pagamento (QR público / POS):
  - exige `APPYPAY` ACTIVE e habilitado para o canal.

- Callback/polling:
  - não foi alterado nesta fase.

## Auditoria
Mudanças de configuração são registradas via `OperationalEventLog` (metadata sanitizada), incluindo:
- bootstrap defaults;
- update;
- activate/deactivate.

## Limitações
- Não implementa novos gateways.
- Não implementa refund/settlement/conciliação.
- Não implementa export PDF/Excel.

## Próximo passo recomendado
- UI administrativa mínima para configuração (frontend).
- Configuração “per unidade”/“per device” se necessário (sem quebrar tenant-level).

## Hardening (Prompt 38.1)
Ver: `docs/tenant-core/38-1-hardening-validacoes-testes-metodos-pagamento.md`
