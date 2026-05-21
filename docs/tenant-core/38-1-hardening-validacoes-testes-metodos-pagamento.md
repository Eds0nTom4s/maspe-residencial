# Prompt 38.1 — Hardening de validações e cobertura de testes (métodos de pagamento tenant-aware)

## O que foi reforçado

### Status
- Apenas `ACTIVE` pode ser usado para iniciar operações.
- `INACTIVE` e `SUSPENDED` são tratados como indisponíveis (bloqueiam uso real).

### Gateway (AppyPay)
- Iniciação de AppyPay exige:
  - método `APPYPAY` em `ACTIVE`;
  - habilitado para o canal (`enabledForQr`/`enabledForPos`);
  - e **gateway configurado** (ou `app.payment.appypay.mock=true`).

Observação importante:
- Callback/polling continuam confirmando pagamentos já iniciados (não quebrar transação pendente).

### Ordens manuais (CASH/TPA)
- Criação de ordem manual valida:
  - método ativo para QR e destino;
  - min/max quando configurado;
  - e exige turno aberto quando o método define `requiresOpenTurno=true`.

- Confirmação manual no POS revalida método (evita confirmar método desativado/suspenso depois da criação).

### Limites
- `minAmount`/`maxAmount` negativos são rejeitados.
- `minAmount > maxAmount` é rejeitado.

## Testes adicionados
- IT QR + método inativo (bloqueio de iniciação AppyPay e filtragem de lista).
- IT admin RBAC (CASHIER read-only).
- IT device/POS (`/device/payment-methods`) garantindo filtro por `enabledForPos` e `ACTIVE-only`.
- IT cross-tenant:
  - QR token resolve tenant correto (sem vazamento entre tenants);
  - device de outro tenant não confirma ordem manual de outro tenant.
- IT manual (CASH):
  - confirmação manual bloqueada se método for desativado após criação;
  - confirmação manual bloqueada se método for suspenso após criação;
  - confirmação manual exige turno aberto.
- IT AppyPay POS:
  - iniciação por POS bloqueada quando `APPYPAY` estiver `INACTIVE`.
- IT polling AppyPay após desativação:
  - pagamento já iniciado continua confirmável via polling manual mesmo se `APPYPAY` for desativado depois.
- Testes unitários para validações adicionais no service/admin.

## Limitações conhecidas
- Config AppyPay ainda é global por ambiente; tenant-aware controla “permitido/visível/ativo”, mas não credenciais por tenant.
- Ainda não existe política por unidade/device (fora do escopo).
