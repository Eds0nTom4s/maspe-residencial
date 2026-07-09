# TURNO_PRE_FECHO_REVALIDACAO_001

## 1. Objectivo

Revalidar o pre-fecho de turno depois do auto-fecho de Sessoes de Consumo no CONSUMA PONTO. A validacao confirma que uma sessao PONTO resolvida e encerrada automaticamente deixa de aparecer no contador `sessoesAbertas` e, por isso, nao bloqueia o fecho normal por esse motivo.

## 2. Base anterior

- Branch base: `backend/consuma-ponto-sessao-consumo-auto-closure-001`.
- Commit base: `2819187`.
- Implementacao anterior: `SessaoConsumoAutoClosureService.tryAutoCloseSessaoConsumo()`.
- Frontend de referencia: `ui/consuma-demo-freezy-app-pedidos-001`, commit `114d604eaa7370646997bb8479e8e3cbdacb2c56`.

## 3. Problema auditado

Antes do auto-fecho, uma sessao de consumo PONTO podia permanecer `ABERTA` depois do pedido estar resolvido. O pre-fecho lia essa sessao como pendencia operacional e bloqueava o turno, mesmo quando o fluxo do cliente ja tinha terminado.

## 4. O que o auto-fecho resolve

Quando a sessao pertence ao CONSUMA PONTO, tem pelo menos um pedido, todos os pedidos estao terminais, nao existe pendencia financeira, nao ha ordem de pagamento ativa e nao ha subpedido pendente, o backend encerra a sessao como `ENCERRADA`. O pre-fecho recalculado deixa de contar essa sessao como aberta.

## 5. Como o pre-fecho calcula sessoes abertas

O endpoint `GET /tenant/operacao/turnos/{turnoId}/pre-fecho` chama `TenantOperacaoController.preFecho`, depois `TurnoOperacionalService.preFecho`, e finalmente `TurnoResumoService.calcularPreFecho`.

O contador usa `SessaoConsumoRepository.countByTenantIdAndUnidadeAtendimentoIdAndStatusIn`, filtrando tenant, unidade de atendimento e estados operacionalmente abertos.

## 6. Estados que entram no contador

- `ABERTA`.
- `AGUARDANDO_PAGAMENTO`.

## 7. Estados que nao entram no contador

- `ENCERRADA`.
- `EXPIRADA`.

`ENCERRADA` representa sessao resolvida. `EXPIRADA` representa sessao abandonada e encerrada pelo sistema; nao e sessao operacionalmente aberta, embora continue auditavel.

## 8. Bloqueios duros

- Subpedido nao terminal no turno.
- Pedido nao terminal no turno.
- Sessao de consumo `ABERTA` ou `AGUARDANDO_PAGAMENTO` na unidade do turno.
- Alerta financeiro critico quando `PagamentoPendenteQueryService.alertasPorTurno(...).isBloqueiaFecho()` retorna `true`.

## 9. Alertas flexiveis

- Pagamento gateway `PENDENTE` quando nao classificado como bloqueio critico.
- Dispositivo offline por heartbeat stale.
- Alertas financeiros nao bloqueantes.

## 10. Comportamento em CONSUMA PONTO

O PONTO pode operar com cozinha opcional, pedido rapido e sessao anonima sem mesa. Pedido finalizado ou cancelado sem pendencias pode acionar auto-fecho da sessao. Pedido nao terminal, financeiro pendente ou ordem ativa continuam impedindo encerramento automatico e podem bloquear o pre-fecho por regra real.

## 11. Comportamento em REST/KDS

REST/KDS nao e convertido para comportamento PONTO. Sessoes de mesa abertas continuam sendo pendencias operacionais. Subpedidos nao terminais continuam bloqueando. O KDS nao e contornado.

## 12. Relacao com ordem de pagamento

`SessaoConsumoAutoClosureService` nao fecha sessoes que possuam `OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO`. Ordens confirmadas, canceladas ou expiradas nao sao tratadas como ordem ativa pelo auto-fecho. O pre-fecho tambem recebe alertas financeiros derivados de pagamentos pendentes do turno.

## 13. Relacao com pedido

Pedidos `CRIADO` ou `EM_ANDAMENTO` sao nao terminais e bloqueiam o pre-fecho. Pedidos `FINALIZADO` e `CANCELADO` sao terminais. O teste de revalidacao preserva o bloqueio de pedido nao terminal mesmo quando a sessao esta irregularmente encerrada.

## 14. Relacao com subpedido

Subpedidos diferentes de `ENTREGUE` ou `CANCELADO` bloqueiam o pre-fecho. Esta regra preserva REST/KDS e producao obrigatoria quando aplicavel.

## 15. Relacao com dispositivo offline

Dispositivo offline e contado por `DispositivoOperacionalRepository.countOfflineByTenantAndUnidadeAtendimento(...)`. A regra atual adiciona aviso, nao bloqueio duro.

## 16. Fora do escopo

Nao foi alterado frontend, Gestao de Turnos visual, Cardapio Publico, Acompanhamento Publico, App Pedidos, fecho forcado, extrato, gateway, estorno, PDV, caixa, fiscal, KDS, REST demo, SERVICE ou DELIVERY. Tambem nao foram criados novos estados de turno ou de sessao.

## 17. Riscos restantes

- O DTO ainda expoe `sessoesAbertas` como contador agregado, sem breakdown por estado.
- Ordens expiradas aparecem apenas indiretamente pelo estado financeiro/alertas existentes.
- Sessoes sem pedido continuam dependentes da regra operacional existente: nao sao auto-fechadas por `SessaoConsumoAutoClosureService`.

## 18. Recomendacoes para proxima fase

- Expor breakdown opcional de sessoes por status no pre-fecho, sem mudar o contrato atual.
- Criar indicador explicito para sessoes sem pedido.
- Separar no DTO avisos operacionais de avisos financeiros para leitura mais clara no frontend.
