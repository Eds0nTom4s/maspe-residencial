# Prompt 9.3 — Monitoramento financeiro mínimo e reconciliação operacional (tenant-aware)

Data: 2026-05-16

## Objetivo

Adicionar camada de **monitoramento e auditoria operacional** (leitura) para pagamentos e callbacks AppyPay, com visão:

- por tenant (TENANT_ADMIN / suporte do cliente)
- global (PLATFORM_ADMIN / suporte CONSUMA)

Sem implementar nesta fase:

- conciliação bancária formal por extrato
- settlement/split
- liquidação para comerciante
- estornos/reembolsos
- faturação/subscrição

## Conceitos

- **Pagamento pendente**: `Pagamento.status = PENDENTE`
- **Pagamento confirmado**: `Pagamento.status = CONFIRMADO`
- **Pagamento preso**: pendente com idade acima do timeout (configurável)
- **Divergência básica** (heurística):
  - callback com `processingStatus` em `FAILED/INVALID_SIGNATURE/PAYMENT_NOT_FOUND`
  - callback com erro de valor divergente
  - pagamento confirmado mas pedido não está `PAGO`
  - pagamento pendente “antigo”

Nesta fase, divergência é **computada** (DTO), não persistida como novo estado.

## Segurança e multi-tenant

- Endpoints `tenant/*` exigem `TenantContext` e validam membership via `TenantGuard.assertCurrentUserBelongsToTenant`.
- Endpoints `platform/*` exigem `TenantGuard.assertPlatformAdmin`.
- **Logs sem tenant** (ex.: `PAYMENT_NOT_FOUND`) ficam acessíveis apenas via platform.
- Tenant-admin recebe apenas resumos; **não expomos raw callback body** no tenant.

## Endpoints

### Tenant

Base: `/api/tenant/financeiro`

- `GET /pagamentos`
- `GET /pagamentos/{pagamentoId}`
- `GET /callbacks`
- `GET /resumo`

### Platform

Base: `/api/platform/financeiro`

- `GET /pagamentos` (opcional `tenantId`)
- `GET /callbacks` (opcional `tenantId`)
- `GET /callbacks/sem-tenant`
- `GET /callbacks/{id}` (detalhe com raw)
- `GET /resumo`

## Configuração

Timeout para sinalizar pendências antigas:

- `consuma.pagamentos.monitoramento.pendente-timeout-minutos` (default: 30)

## Próximos passos (fase futura)

- consulta ativa ao gateway para pendências REF (polling controlado)
- reconciliação por extrato/arquivo do gateway
- alertas (email/Slack) e painéis de operação
- estorno/reembolso com auditoria e trilha de eventos

