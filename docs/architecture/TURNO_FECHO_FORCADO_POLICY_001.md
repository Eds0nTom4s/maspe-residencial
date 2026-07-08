# TURNO_FECHO_FORCADO_POLICY_001

## 1. Objectivo

Consolidar a politica de fecho forcado de turno para que a acao seja autorizada, justificada, auditada e baseada no mesmo motor de pre-fecho usado pelo fecho normal.

## 2. Problema operacional

Antes desta fase, `forcarFecho=true` funcionava como bypass amplo: o service exigia apenas observacao nao vazia e permitia fechar o turno mesmo com bloqueios de pre-fecho. Isso podia deixar sessoes, pedidos ou subpedidos pendentes sem metadata operacional explicita.

## 3. Diferenca entre fecho normal e fecho forcado

Fecho normal: permitido para owner, admin, operator e cashier quando o pre-fecho nao possui bloqueios.

Fecho forcado: permitido apenas para role superior, exige motivo valido, tenta auto-fechar sessoes PONTO elegiveis, recalcula o pre-fecho, rejeita bloqueio financeiro critico e registra as pendencias preservadas.

## 4. Quem pode forcar

O backend usa `TurnoOperacionalPolicy.assertCanForceClose`.

Autorizados:

- `TENANT_OWNER`
- `TENANT_ADMIN`
- `PLATFORM_ADMIN`, pelo bypass ja existente de platform admin em `assertHasAny`

Nao autorizados:

- `TENANT_OPERATOR`
- `TENANT_CASHIER`
- roles de cozinha, leitura ou financeiro sem perfil superior

## 5. Motivo obrigatorio

`FecharTurnoRequest` passou a aceitar `motivoFechoForcado`.

Regra:

- obrigatorio quando `forcarFecho=true`
- fallback controlado para `observacao`, preservando compatibilidade backend
- minimo de 10 caracteres
- maximo de 500 caracteres
- armazenado em `TurnoOperacional.observacaoFecho`
- registrado em `OperationalEventLog.metadataJson`
- persistido em `resumo_json.fechoForcadoPolicy`

## 6. Bloqueios ignoraveis

Com motivo e role superior, a politica permite fechar com pendencias operacionais herdadas:

- sessoes operacionalmente abertas ainda nao elegiveis a auto-fecho
- pedidos nao terminais
- subpedidos nao terminais
- dispositivos offline
- pagamentos pendentes nao criticos, quando o alerta financeiro nao bloqueia

Essas pendencias nao sao apagadas e nao mudam de estado.

## 7. Bloqueios nao ignoraveis

Nesta fase, o bloqueio nao ignoravel implementado a partir da regra real do pre-fecho e:

- `alertasFinanceiros.bloqueiaFecho=true`, classificado como `ALERTA_FINANCEIRO_CRITICO_BLOQUEANTE`

Tambem continuam nao ignoraveis por regras ja existentes:

- turno `FECHADO`
- turno `CANCELADO`
- tenant/unidade/turno inexistente
- falha ao gerar snapshot financeiro confiavel

## 8. Alertas flexiveis

Alertas flexiveis continuam vindo do pre-fecho:

- pagamento pendente nao critico
- dispositivo offline
- alerta financeiro nao bloqueante

Eles sao registrados em metadata quando o fecho forcado e permitido.

## 9. Consequencia sobre sessoes

Antes do fecho forcado, o backend lista sessoes da unidade em `ABERTA` ou `AGUARDANDO_PAGAMENTO` e chama `SessaoConsumoAutoClosureService.tryAutoCloseSessaoConsumo`.

Consequencias:

- sessao PONTO elegivel e encerrada automaticamente
- sessao PONTO nao elegivel permanece aberta
- sessao REST/KDS nao e encerrada pela regra PONTO
- totais de tentativas e encerramentos sao registrados em `fechoForcadoPolicy`

## 10. Consequencia sobre pedidos

Fecho forcado nao cancela, nao finaliza e nao altera pedido. Pedidos pendentes permanecem no estado original e sao reportados em `pendenciasHerdadas.pedidosNaoTerminais`.

## 11. Consequencia sobre pagamentos

Fecho forcado nao confirma pagamento, nao expira ordem de pagamento, nao cria estorno, nao cria caixa e nao implementa gateway. Pagamentos pendentes permanecem no estado original e sao reportados em metadata.

## 12. Auditoria

Evento de sucesso:

- `TURNO_FECHADO_FORCADO`

Evento de bloqueio por pendencia nao ignoravel:

- `TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO`

Metadata registrada:

- policy
- turnoId
- tenantId
- unidadeAtendimentoId
- actorUserId
- actorRoles
- motivoFechoForcado
- bloqueiosPreFecho
- bloqueiosIgnorados
- bloqueiosNaoIgnoraveis
- avisosFlexiveis
- sessoesAutoFechoTentadas
- sessoesAutoFechadas
- pendenciasHerdadas
- efeitoEmPedidosPagamentosOrdens

## 13. Relacao com pre-fecho

O fecho forcado usa `TurnoResumoService.calcularPreFecho`. Apos a tentativa de auto-fecho PONTO, o pre-fecho e recalculado antes da classificacao final.

## 14. Relacao com extrato

O extrato financeiro nao foi alterado. O snapshot financeiro existente continua em `resumo_json.financeiro`. A politica de fecho forcado e gravada em chave separada, `resumo_json.fechoForcadoPolicy`.

## 15. O que ficou fora do escopo

- frontend
- visual da Gestao de Turnos
- Cardapio Publico
- Acompanhamento Publico
- App Pedidos
- extrato financeiro
- gateway
- estorno
- PDV
- caixa
- fiscal/invoice
- KDS
- REST demo
- SERVICE
- DELIVERY

## 16. Riscos restantes

- O frontend atual pode continuar enviando apenas `observacao`; o backend aceita fallback, mas uma fase UI deve expor `motivoFechoForcado` explicitamente.
- Nao foi criada uma feature de pendencia herdada no turno seguinte; a pendencia fica auditada em metadata.
- A classificacao de novos bloqueios nao ignoraveis depende de futuras regras do pre-fecho.

## 17. Proxima fase recomendada

Criar uma fase UI para mostrar o motivo obrigatorio, os bloqueios ignorados, os bloqueios nao ignoraveis e as pendencias herdadas antes de confirmar o fecho forcado.
