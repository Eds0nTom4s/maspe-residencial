# TURNO_EXTRATO_LIMPEZA_OPERACIONAL_001

## 1. Objectivo

Refinar o extrato de turno para funcionar como snapshot operacional e financeiro do momento de fecho, e definir limpeza operacional como classificacao explicita de pendencias sem apagar dados nem alterar estados de negocio.

## 2. Contexto das tres fases anteriores

Fase 1: auto-fecho de sessao PONTO encerra sessoes elegiveis quando pedidos, financeiro, ordens e subpedidos estao resolvidos.

Fase 2: pre-fecho passou a contar sessoes abertas apenas em `ABERTA` e `AGUARDANDO_PAGAMENTO`, excluindo `ENCERRADA` e `EXPIRADA`.

Fase 3: fecho forcado passou a exigir role superior, motivo valido, pre-fecho recalculado, tentativa de auto-fecho PONTO e respeito a bloqueios nao ignoraveis.

## 3. Conceito de extrato como snapshot

O extrato persistido em `TurnoOperacional.resumoJson` representa uma fotografia do momento de fecho. Ele deve ser auditavel posteriormente sem depender de recalculo livre do estado vivo.

Nesta fase, o fechamento grava:

- campos retrocompativeis do pre-fecho no root do JSON
- `financeiro`, com o snapshot financeiro assinado ja existente
- `operacional`, com identificacao, pedidos, subpedidos, sessoes, pagamentos, ordens, dispositivos, pre-fecho e auditoria
- `limpezaOperacional`, com pendencias herdadas e garantias de nao mutacao
- `fechoForcadoPolicy`, quando aplicavel

## 4. Diferenca entre extrato e estado vivo

Estado vivo e o conjunto atual de pedidos, pagamentos, ordens, sessoes e dispositivos. Pode evoluir depois do fecho, por exemplo por tratamento operacional posterior.

Extrato e a fotografia persistida no turno fechado. O backend nao deve recalcular ou alterar essa fotografia sem auditoria propria.

## 5. Campos minimos do snapshot

Identificacao:

- `turnoId`
- `tenantId`
- `instituicaoId`
- `unidadeAtendimentoId`
- `abertoEm`
- `fechadoEm`
- `fechadoPor`
- `fechadoPorRole`
- `tipoFecho`
- `motivoFechoForcado`
- `policyVersion`

Operacional:

- pedidos mapeados e contagem por status
- subpedidos por status e nao terminais
- sessoes abertas, encerradas, expiradas e herdadas
- dispositivos offline
- bloqueios e alertas no momento

Financeiro:

- pagamentos mapeados por status
- pagamentos confirmados, pendentes, falhados e estornados
- ordens ativas, confirmadas, canceladas, expiradas e vencidas ainda ativas
- total confirmado, pendente, falhado, cancelado e estornado
- total por metodo
- total por origem
- hash e assinatura do snapshot financeiro quando habilitados

Auditoria:

- `snapshotGeradoEm`
- `policyVersion`
- hash/assinatura financeira existente
- `fechoForcadoPolicy` quando o fecho e forcado

## 6. Fecho normal

Quando o pre-fecho nao tem bloqueios duros, o turno fecha normalmente.

O snapshot registra `tipoFecho=NORMAL`, nao registra motivo de fecho forcado e classifica a limpeza operacional como `SEM_PENDENCIAS` quando nao ha bloqueios, alertas ou ordens ativas.

Pagamentos confirmados entram em `totalConfirmado`. Valores pendentes permanecem separados em `totalPendente`.

## 7. Fecho forcado

Quando `forcarFecho=true`, o backend preserva a politica aprovada:

- valida role superior
- exige motivo
- tenta auto-fecho PONTO elegivel
- recalcula pre-fecho
- bloqueia alerta financeiro critico nao ignoravel
- fecha apenas se os bloqueios remanescentes forem herdaveis

O snapshot registra `tipoFecho=FORCADO`, motivo, bloqueios ignorados, alertas e pendencias herdadas.

## 8. Fecho bloqueado

Quando ha bloqueio nao ignoravel, o turno nao fecha e nao recebe snapshot final de turno fechado.

A tentativa pode gerar auditoria de bloqueio quando o padrao existente suporta, mas pedidos, pagamentos, ordens e sessoes permanecem no estado original.

## 9. Pendencias herdadas

Pendencias herdadas ficam em `resumo_json.limpezaOperacional.pendenciasHerdadas`.

Incluem:

- sessoes abertas ou aguardando pagamento
- pedidos nao terminais
- subpedidos nao terminais
- pagamentos pendentes
- alertas financeiros
- dispositivos offline
- ordens ativas, expiradas e vencidas ainda ativas

Nesta fase nao foi criada migracao ampla para turno seguinte.

## 10. Sessoes abertas

O pre-fecho considera sessoes abertas apenas nos estados:

- `ABERTA`
- `AGUARDANDO_PAGAMENTO`

O snapshot operacional preserva:

- `sessoesAbertas`, vindo do pre-fecho
- `sessoesEncerradas`, sessoes com pedido do turno em `ENCERRADA`
- `sessoesExpiradas`, sessoes com pedido do turno em `EXPIRADA`
- `sessoesHerdadas`, sessoes da unidade ainda em `ABERTA` ou `AGUARDANDO_PAGAMENTO`

## 11. Pedidos

Pedidos sao fonte de verdade operacional.

O extrato registra contagens por status e totais mapeados. O fechamento nao cancela, nao finaliza e nao altera pedido.

## 12. Pagamentos

Pagamentos e ordens confirmadas sao fonte de verdade financeira.

O extrato separa valores confirmados, pendentes, falhados, cancelados e estornados. Ordem expirada ou ativa nao entra em `totalConfirmado`.

## 13. Ordens de pagamento

O snapshot passa a registrar ordens por status:

- `AGUARDANDO_CONFIRMACAO`
- `CONFIRMADA`
- `CANCELADA`
- `EXPIRADA`

Tambem registra `ordensPagamentoVencidasAindaAtivas`, para expor ordem cujo `expiresAt` passou mas cujo estado ainda esta `AGUARDANDO_CONFIRMACAO`.

O comportamento de expiracao nao foi alterado.

## 14. Dispositivos offline

Dispositivo offline permanece indicador operacional. Ele nao altera valores financeiros e e registrado com `impactoFinanceiro=NAO_APLICAVEL`.

## 15. Integridade financeira

`totalConfirmado` vem do relatorio financeiro e do snapshot financeiro congelado, baseado em ordens confirmadas e pagamentos gateway confirmados.

`totalPendente` fica separado. `totalFalhado`, `totalCancelado` e `totalEstornado` tambem ficam separados. Pedido cancelado nao infla valor confirmado.

## 16. O que a limpeza operacional faz

Limpeza operacional faz classificacao:

- registra pendencias herdadas
- registra sessoes abertas remanescentes
- registra pedidos e subpedidos nao terminais
- registra pagamentos pendentes e ordens ativas/expiradas
- registra dispositivos offline
- indica se o turno fechou com pendencias

## 17. O que a limpeza operacional nao faz

Nao apaga sessoes, nao encerra REST/KDS, nao cancela pedido, nao finaliza pedido, nao confirma pagamento, nao estorna pagamento, nao muda metodo, nao cria caixa, nao cria fiscal e nao altera gateway.

## 18. Auditoria

O fechamento continua gerando eventos existentes:

- `TURNO_FECHADO`
- `TURNO_FECHADO_FORCADO`
- `TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO`, quando aplicavel

O `resumo_json` do turno fechado contem a fotografia operacional e financeira usada na decisao.

## 19. Riscos restantes

- Nao ha feature nova de migracao de pendencias para o turno seguinte.
- Ordens vencidas ainda ativas sao registradas, mas nao expiradas automaticamente nesta fase.
- O frontend ainda nao mostra o novo bloco `operacional`/`limpezaOperacional`.
- Novos tipos de bloqueio nao ignoravel devem ser adicionados explicitamente em fases futuras.

## 20. Proxima fase recomendada

Criar uma fase de UI/API de leitura para exibir o extrato congelado, pendencias herdadas e diferenca entre valor confirmado e valor pendente sem recalcular o turno fechado como estado vivo.
