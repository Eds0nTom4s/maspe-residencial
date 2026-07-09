# Prompt 35 — Observabilidade operacional do piloto + suporte interno

## Objetivo
Criar uma camada **interna** (platform) de observabilidade operacional para o piloto controlado da CONSUMA, permitindo que a equipa CONSUMA:
- acompanhe saúde por tenant/unidade/turno;
- identifique rapidamente devices offline, pagamentos críticos e filas de produção atrasadas;
- consulte eventos operacionais recentes;
- use playbooks mínimos para suporte.

Esta fase é **backend/API**, sem dashboard frontend complexo.

## Princípios
- **Somente roles internas** acessam observabilidade: `PLATFORM_ADMIN` (nesta fase).
- **Não expor dados sensíveis**: deviceToken, activationCode, cursors, secrets, payloads brutos de gateway/callback.
- **Paginação e limites**: endpoints com `page/size` e `max-page-size`.
- **Lookback default**: evitar consultas “sem fim” por padrão.
- **Alertas calculados on-the-fly** (sem tabela persistida nesta fase).

## Rotas (platform)
Base: `/platform/observabilidade`

### Saúde global
- `GET /platform/observabilidade/saude`

### Tenants
- `GET /platform/observabilidade/tenants`
- `GET /platform/observabilidade/tenants/{tenantId}`
- `GET /platform/observabilidade/tenants/{tenantId}/turnos`
- `GET /platform/observabilidade/tenants/{tenantId}/devices`
- `GET /platform/observabilidade/tenants/{tenantId}/pagamentos`
- `GET /platform/observabilidade/tenants/{tenantId}/producao`
- `GET /platform/observabilidade/tenants/{tenantId}/eventos`

### Alertas ativos
- `GET /platform/observabilidade/alertas`

## Alertas (MVP)
Alertas são gerados por heurísticas simples e **não** são persistidos nesta fase.

Tipos:
- `TENANT_PAGAMENTO_CRITICO` (pendente muito antigo ou `MAX_ATTEMPTS_REACHED`)
- `TENANT_DEVICE_OFFLINE` (heartbeat stale)
- `TENANT_TURNO_EM_FECHO_LONGO` (turno `EM_FECHO` por muito tempo)
- `TENANT_SEM_TURNO_ABERTO` (informativo)

Levels:
- `INFO`, `WARNING`, `CRITICAL`

Actions:
- `MONITOR`, `CHECK_GATEWAY`, `CHECK_DEVICES`, `CHECK_TURNOS`, `CONTACT_TENANT`, `SUPPORT_REQUIRED`

## Configurações
`application.properties`:
- `consuma.observabilidade.device-offline-threshold-minutes`
- `consuma.observabilidade.producao-subpedido-atrasado-minutes`
- `consuma.observabilidade.turno-em-fecho-longo-minutes`
- `consuma.observabilidade.eventos-default-lookback-hours`
- `consuma.observabilidade.max-page-size`

Observação: limiares financeiros (warning/critical de pendências) continuam em:
- `consuma.financeiro.pending-payments.*`

## Segurança e sanitização
- Endpoints **não retornam**: tokens, activationCode, hashes de token, secrets, payload bruto de gateway/callback.
- Eventos operacionais retornam `metadataResumo` (somente tamanho), nunca `metadataJson` completo.

## Como usar no piloto (operacional)
Checklist de rotina diária da equipa CONSUMA durante piloto:
1. `GET /platform/observabilidade/saude` (visão geral).
2. `GET /platform/observabilidade/tenants?comPagamentosCriticos=true` (priorizar risco financeiro).
3. `GET /platform/observabilidade/tenants?comDevicesOffline=true` (priorizar device/rede).
4. `GET /platform/observabilidade/tenants/{tenantId}` (detalhe de um tenant em problema).
5. Se necessário: consultar eventos recentes do tenant e cruzar com turno/pagamentos.

## Limitações (intencionais)
- Não é APM completo.
- Não é dashboard visual.
- Não inclui locks distribuídos (multi-instância) para jobs — assunto de hardening/escala.
- Não substitui processos de suporte (playbooks e disciplina operacional são essenciais).

## Próximos passos sugeridos
- Dashboards internos simples (Grafana/Actuator/Micrometer) e alertas automatizados.
- Locks distribuídos para jobs em multi-instância.
- Retenção/particionamento do `OperationalEventLog` em volume alto.

