# Prompt 37.1 — Snapshot financeiro no fecho do turno

## Objetivo

Persistir, no `resumo_json` do `TurnoOperacional`, uma **fotografia financeira operacional** congelada **no momento do fecho** do turno.

Isto fecha a lacuna do Prompt 37: o relatório detalhado é recalculável; o snapshot do fecho é histórico.

## Princípios

- **Callback/polling AppyPay continuam sendo a fonte de confirmação** de pagamentos digitais.
- Pagamento manual (`CASH`/`TPA`) é confirmado pelo POS e considerado confirmado imediatamente.
- O snapshot **não é recalculado** depois que o turno está `FECHADO`.
- O snapshot deve ser salvo **na mesma transação do fecho**. Se falhar, o fecho falha.

## O que entra no `resumo_json`

O `resumo_json` continua contendo os campos do pré-fecho (compatibilidade), e passa a incluir:

- `financeiro`: snapshot sanitizado com:
  - `snapshotVersion` = `37.1`
  - totais por método (CASH/TPA/APPYPAY)
  - totais por destino (pagamento de pedidos vs carregamento de fundo)
  - totais confirmados (manual vs gateway)
  - pendências e divergências (resumidas)
  - `alertasFinanceiros` do turno no momento do fecho

## O que NÃO entra no `resumo_json`

Para evitar risco de vazamento e peso desnecessário, **não** persistimos:

- payload bruto do gateway (AppyPay)
- tokens, deviceToken, secrets, hashes
- listas completas de pagamentos, ordens ou eventos

## Comportamento com pendentes

- Pagamentos/ordens pendentes **aparecem como pendentes** no snapshot.
- Se um AppyPay pendente for confirmado depois do fecho, o relatório atual pode mudar, mas o snapshot do fecho **não muda**.

## Implementação (onde está)

- Snapshot persistido no fecho:
  - `src/main/java/com/restaurante/service/operacao/TurnoOperacionalService.java`
- DTO do snapshot:
  - `src/main/java/com/restaurante/financeiro/caixa/dto/ResumoFinanceiroFechoTurnoSnapshot.java`

## Limitações

- Não é conciliação bancária final.
- Não implementa export assinado.
- Não implementa refund/settlement/fiscalidade.

## Próximo passo recomendado

- Snapshot financeiro “final” no fecho incluindo um hash/assinatura interna (tamper-evident) e endpoint de export lógico (JSON assinado) para auditoria operacional.

