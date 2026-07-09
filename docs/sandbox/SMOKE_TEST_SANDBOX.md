# CONSUMA — Smoke Test (Sandbox Operacional Controlada)

Objetivo: validar rapidamente que a sandbox sobe e permite operar 1 tenant `PONTO` e 1 tenant `RESTA` antes da UI (health, templates, QR público, pagamento mock, device/POS, KDS e turno básico).

## Pré-requisitos

- `docker` + `docker compose`
- `curl`, `jq`, `openssl`
- JAR buildado para o `Dockerfile` (usa `target/sistema-restauracao-1.0.0.jar`):
  - `mvn -DskipTests package`

## 1) Configurar env da sandbox

1. Copiar o exemplo:
   - `cp .env.sandbox.example .env.sandbox`
2. Ajustar:
   - `DB_PASSWORD` (forte)
   - `JWT_SECRET`, `DEVICE_TOKEN_HASH_SECRET`, `SYNC_CURSOR_HMAC_SECRET`, `CONSUMA_SNAPSHOT_HMAC_SECRET_V1` (fortes)
   - `APPYPAY_WEBHOOK_SECRET` (forte)
3. Para sandbox local inicial, o seed `admin/admin123` pode ser usado, mas registrar ressalva e trocar antes de sandbox em servidor.

Recomendação de secrets (exemplo):
- `openssl rand -hex 32`

## 2) Subir stack sandbox limpa

- `docker compose --env-file .env.sandbox -f docker-compose.sandbox.yml down -v`
- `docker compose --env-file .env.sandbox -f docker-compose.sandbox.yml up -d --build`
- `docker compose --env-file .env.sandbox -f docker-compose.sandbox.yml ps`

Nota: `SANDBOX_API_PORT` é usado para mapear a porta externa. Se `8080` estiver ocupada (ex.: stack local), usar `8081`.

## 3) Executar smoke automatizado

- `scripts/sandbox-smoke.sh`

O script:
- gera slugs únicos por execução (baseado em timestamp UTC);
- grava evidências em `/tmp/consuma-sandbox-smoke-<RUN_ID>/`;
- não imprime tokens completos (apenas prefix/len).

## 4) Passos manuais (quando necessário)

O script abre um turno e cria um pedido por POS, mas não fecha o turno nem exporta snapshot/evidence automaticamente, porque:
- o pré-fecho pode bloquear enquanto existirem subpedidos/pedidos/sessões em aberto;
- a resolução envolve transições de status e/ou liquidação de sessão, que pode variar conforme policies.

Checklist manual recomendado após o script:

1. Validar pré-fecho:
   - `GET /api/tenant/operacao/turnos/{turnoId}/pre-fecho`
2. Concluir subpedidos (cozinha/bar) via endpoints tenant:
   - `PATCH /api/tenant/producao/subpedidos/{id}/status` (sequência: `CRIADO -> PENDENTE -> EM_PREPARACAO -> PRONTO -> ENTREGUE`)
3. (Se aplicável) liquidar sessão de consumo (cash) para liberar fecho:
   - `POST /api/sessoes-consumo/{id}/liquidar?metodoPagamento=CASH`
4. Fechar turno:
   - `POST /api/tenant/operacao/turnos/{turnoId}/fechar`
5. Exportar snapshot:
   - `GET /api/tenant/financeiro/turnos/{turnoId}/snapshot/export`
6. Evidence bundle:
   - `GET /api/tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundle`

## 5) Multi-tenant mínimo (verificações)

- Cardápio `PONTO` não lista produtos do `RESTA`
- Pedido público `PONTO` rejeita `produtoId` do `RESTA`
- Device `RESTA` não acessa recursos `PONTO`
