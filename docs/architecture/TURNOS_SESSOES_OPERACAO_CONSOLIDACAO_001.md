# TURNOS_SESSOES_OPERACAO_CONSOLIDACAO_001

## 1. Objectivo

Consolidar, sem nova funcionalidade, a sequencia operacional backend que envolve Sessao de Consumo, Pre-fecho de Turno, Fecho Forcado e Extrato/Limpeza Operacional.

Esta fase valida que as quatro fases anteriores estao presentes, lineares, rastreaveis e regressadas por testes, preservando o fluxo aprovado da Demo Freezy V1 e sem alterar frontend.

## 2. Historico das quatro fases

| Fase | Branch | Commit final | Resultado |
| --- | --- | --- | --- |
| Auto-fecho de Sessao PONTO | backend/consuma-ponto-sessao-consumo-auto-closure-001 | 2819187 | Sessao PONTO resolvida passa a ENCERRADA automaticamente quando nao ha pendencias. |
| Revalidacao do pre-fecho | backend/consuma-turno-pre-fecho-revalidacao-001 | 0b6c908b53133dc80599a01f32fcfc9896103b18 | sessoesAbertas conta apenas ABERTA e AGUARDANDO_PAGAMENTO. |
| Politica de fecho forcado | backend/consuma-turno-fecho-forcado-policy-001 | 2f61d2d67b7449bc8173fe035135985851759090 | Fecho forcado exige role superior, motivo, pre-fecho recalculado e auditoria. |
| Extrato e limpeza operacional | backend/consuma-turno-extrato-limpeza-operacional-001 | 02363b74c115bed2599764c79d49410dd9b775dd | Fecho persiste snapshot operacional/financeiro e pendencias herdadas sem mutacao indevida. |

Branch de consolidacao: `backend/consuma-turnos-sessoes-operacao-consolidacao-001`.

## 3. Auto-fecho de Sessao PONTO

Service principal: `SessaoConsumoAutoClosureService`.

A regra fecha automaticamente apenas sessoes associadas a templates PONTO (`CONSUMA_PONTO_V1` e `CONSUMA_PONTO_QR`) quando:

- a sessao esta `ABERTA` ou `AGUARDANDO_PAGAMENTO`;
- existe pelo menos um pedido;
- todos os pedidos estao terminais;
- nao ha pedido nao cancelado com `NAO_PAGO` ou `PENDENTE_PAGAMENTO`;
- nao ha subpedido obrigatorio pendente;
- nao ha `OrdemPagamento` em `AGUARDANDO_CONFIRMACAO`.

Sessoes `ENCERRADA` ou `EXPIRADA` sao idempotentes. Sessoes sem pedido nao sao encerradas por esta regra. REST/KDS e QR mesa REST nao herdam comportamento PONTO.

## 4. Pre-fecho de Turno

Service principal: `TurnoResumoService`.

O contador `sessoesAbertas` usa `SessaoConsumoRepository.countByTenantIdAndUnidadeAtendimentoIdAndStatusIn` com os estados:

- `ABERTA`;
- `AGUARDANDO_PAGAMENTO`.

Estados excluidos:

- `ENCERRADA`;
- `EXPIRADA`.

O pre-fecho continua bloqueando pendencias reais:

- pedidos nao terminais;
- subpedidos nao terminais;
- sessoes operacionalmente abertas;
- alertas financeiros criticos configurados como bloqueantes.

Avisos flexiveis continuam separados, incluindo pagamentos pendentes nao criticos e dispositivos offline.

## 5. Fecho Forcado

Service principal: `TurnoOperacionalService`.

Politica consolidada:

- `TENANT_OWNER` e `TENANT_ADMIN` podem forcar;
- `TENANT_OPERATOR` e `TENANT_CASHIER` nao podem forcar;
- `motivoFechoForcado` ou `observacao` e obrigatorio;
- motivo normalizado precisa ter entre 10 e 500 caracteres;
- pre-fecho e recalculado;
- sessoes PONTO elegiveis sao tentadas para auto-fecho antes da decisao final;
- alerta financeiro critico bloqueante nao e ignoravel;
- bloqueios ignorados e pendencias herdadas ficam auditados;
- pedidos, pagamentos, ordens e sessoes REST/KDS nao sao mutados para permitir o fecho.

## 6. Extrato e Limpeza Operacional

Persistencia: `TurnoOperacional.resumoJson`.

O fecho grava:

- `financeiro`: snapshot financeiro preservado, versao 37.1, com integridade quando habilitada;
- `operacional`: fotografia operacional do momento do fecho;
- `limpezaOperacional`: classificacao sem mutacao de estado;
- `policyVersion`: `TURNO_EXTRATO_LIMPEZA_OPERACIONAL_001`;
- `fechoForcadoPolicy`: metadata de fecho forcado quando aplicavel.

O extrato nao altera pedidos, pagamentos, ordens, sessoes, KDS, REST ou caixa.

## 7. Relacao entre Sessao, Pedido, Pagamento, OrdemPagamento e Turno

Sessao PONTO resolvida reduz bloqueio operacional apenas quando as obrigacoes do pedido e do pagamento estao satisfeitas. O turno nao e fechado automaticamente.

Pedido nao terminal e subpedido nao terminal continuam bloqueando pre-fecho, mesmo que a sessao esteja irregularmente encerrada.

`OrdemPagamento` ativa impede auto-fecho de sessao PONTO. No extrato, ordens ativas, confirmadas, canceladas, expiradas e vencidas ainda ativas ficam visiveis sem virar receita confirmada.

Pagamentos confirmados, pendentes, falhados, cancelados e estornados permanecem separados.

## 8. Comportamento no CONSUMA PONTO

Fluxo validado:

QR publico -> pedido -> aceite -> ordem expiravel -> confirmacao TPA -> PAGO -> entrega -> FINALIZADO -> sessao ENCERRADA automaticamente -> pre-fecho sem bloqueio dessa sessao.

Se houver pagamento pendente, ordem ativa, pedido nao terminal ou subpedido pendente, a sessao nao e auto-encerrada e o pre-fecho continua bloqueando quando aplicavel.

## 9. Proteccao REST/KDS

REST/KDS nao recebe auto-fecho agressivo.

Sessoes REST/KDS abertas, producao pendente e subpedidos obrigatorios nao terminais continuam protegidos por bloqueios de pre-fecho e por testes de integracao existentes.

## 10. O que nao foi alterado

Nao foram alterados:

- frontend;
- visual da Gestao de Turnos;
- Cardapio Publico;
- Acompanhamento Publico;
- App Pedidos;
- gateway;
- estorno;
- PDV;
- caixa;
- fiscal/invoice;
- KDS;
- REST demo;
- SERVICE;
- DELIVERY.

## 11. Testes executados

Testes obrigatorios executados nesta consolidacao:

- `mvn -DskipTests compile`;
- `mvn -Dtest=*SessaoConsumo* test`;
- `mvn -Dtest=*Turno* test` e equivalente Failsafe;
- `mvn -Dtest=*Extrato* test`;
- `mvn -Dtest=*Snapshot* test` e equivalente Failsafe;
- `mvn -Dtest=*ConsumaDemoFreezy* test` e equivalente Failsafe;
- `mvn test`;
- `mvn -Pit verify`.

Resultados completos estao no relatorio `docs/reports/fullstack/RELATORIO_TURNOS_SESSOES_OPERACAO_CONSOLIDACAO_001.txt`.

## 12. Riscos restantes

- Frontend nao foi alterado nesta fase; compatibilidade foi justificada por ausencia de alteracao de contrato visual ou arquivos UI.
- Comandos focados Surefire que selecionam ITs continuam falhando pelo guard do projeto; o runner correto e Failsafe com perfil `it`.
- Nao ha suite nomeada `*Extrato*`; a cobertura real de extrato esta em `*Turno*` e `*Snapshot*`.
- Pendencias herdadas sao classificadas no snapshot, nao migradas automaticamente para outro turno.
- Ordens vencidas ainda ativas sao expostas no snapshot, mas nao expiradas por esta fase.

## 13. Decisao de merge

Decisao tecnica: APROVADO PARA MERGE.

`mvn test` e `mvn -Pit verify` passaram nesta consolidacao. O merge automatico na `develop` nao foi executado por escopo.
