# Playbooks — Piloto operacional CONSUMA (mínimo viável)

Este documento descreve respostas operacionais mínimas para suporte durante o piloto controlado.

## 1) Device offline
1. Consultar devices do tenant: `GET /platform/observabilidade/tenants/{tenantId}/devices?offline=true`.
2. Ver `ultimoHeartbeatEm` e `offlineMinutes`.
3. Validar conectividade local (internet) e energia no ponto.
4. Confirmar `status` do device (ATIVO vs SUSPENSO/REVOGADO).
5. Se recorrente: trocar device ou investigar rede (roteador/firewall).
6. Registrar ocorrência e hora (para correlação com pedidos/pagamentos).

## 2) Pagamento crítico pendente
1. Consultar pagamentos críticos: `GET /platform/observabilidade/tenants/{tenantId}/pagamentos?criticalOnly=true`.
2. Ver `pollingAttempts`, `lastErrorCode`, `nextPollingAttemptAt`.
3. Se cliente apresentou comprovativo: orientar o tenant a usar polling manual via endpoint tenant (FINANCE/ADMIN/OWNER).
4. Se gateway está instável: monitorar erros e reduzir fricção operacional (ex.: orientar espera/alternativa).
5. Nunca “marcar pago manualmente” nesta fase.

## 3) Callback atrasado / inconsistência aparente
1. Ver idade da pendência (`idadeMinutos`) e se já passou do limiar crítico.
2. Confirmar que polling automático está a correr (há `lastPollingAttemptAt` recente).
3. Se necessário, acionar polling manual (tenant) e observar eventos.
4. Se confirmado no gateway mas não no backend: tratar como incidente e escalar (risco de perda de confiança).

## 4) Turno em fecho longo
1. Consultar turnos do tenant: `GET /platform/observabilidade/tenants/{tenantId}/turnos?status=EM_FECHO`.
2. Ver bloqueios/alertas no pré-fecho do tenant (operacional/financeiro).
3. Resolver pendências: subpedidos em aberto, sessões abertas, pagamentos críticos.
4. Se forçar fecho: somente OWNER/ADMIN com observação, e registrar motivo.

## 5) Produção atrasada
1. Consultar produção do tenant: `GET /platform/observabilidade/tenants/{tenantId}/producao`.
2. Identificar unidade com `maisAntigoEmPreparacao` e `atrasados`.
3. Verificar se devices KDS estão online e recebendo atualizações.
4. Confirmar se há gargalo operacional real (pico) ou problema técnico.
5. Registrar ocorrência para aprendizado (produto/processo).

