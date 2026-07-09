# Prompt 34 — Integrar alertas financeiros no pré-fecho do turno

## Objetivo

Consolidar a visão operacional de fecho anexando os alertas financeiros (pagamentos pendentes/polling) ao `pré-fecho` do turno.

- Reutiliza `PagamentoPendenteQueryService.alertasPorTurno(...)` (Fase 33).
- Respeita `consuma.financeiro.pending-payments.block-turno-close-on-critical`.
- Não altera callback AppyPay, polling automático, nem fluxos POS/QR.

## Mudanças principais

### TurnoPreFechoResponse

Agora inclui:
- `alertasFinanceiros` (`TurnoPagamentoAlertasResponse`)
- `possuiAlertasFinanceiros`
- `possuiAlertasFinanceirosCriticos`

### Pré-fecho

`TurnoResumoService.calcularPreFecho(...)` passa a:
- calcular pré-fecho operacional como antes
- anexar alertas financeiros do turno
- quando `alertasFinanceiros.bloqueiaFecho=true`, adiciona bloqueio e `podeFechar=false`
- quando há críticos mas bloqueio desativado, adiciona aviso forte

### Fecho

`TurnoOperacionalService.fechar(...)`:
- se bloqueado por alerta financeiro crítico (property ON), registra `TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO`
- se fechar com pendências financeiras, registra `TURNO_FECHADO_COM_ALERTA_FINANCEIRO`

## Property

- `consuma.financeiro.pending-payments.block-turno-close-on-critical=false` (default)

Quando `true`:
- críticos financeiros bloqueiam fecho normal (409), mas fecho forçado (OWNER/ADMIN) continua possível.

