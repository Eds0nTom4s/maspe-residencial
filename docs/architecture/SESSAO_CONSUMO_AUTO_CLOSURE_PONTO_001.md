# SESSAO_CONSUMO_AUTO_CLOSURE_PONTO_001

**PROMPT:** PROMPT-BACKEND-CONSUMA-SESSAO-CONSUMO-AUTO-CLOSURE-PONTO-001  
**Data:** 2026-07-08  
**Branch:** backend/consuma-ponto-sessao-consumo-auto-closure-001  
**Base:** backend/consuma-demo-freezy-happy-path-e2e-001 @ 6df9089f7262eb5b25772c4bac78e5a0e68916ee

---

## 1. Objectivo

Implementar o auto-fecho da Sessão de Consumo exclusivamente para o fluxo **CONSUMA PONTO**, quando todas as obrigações operacionais e financeiras da sessão estiverem resolvidas.

Reduzir sessões abertas esquecidas e eliminar bloqueios indevidos no fecho normal do turno.

---

## 2. Problema Operacional

Sessões de consumo abertas esquecidas bloqueiam o fecho normal do turno.

Quando a equipa força o fecho do turno, o turno fecha, mas as sessões continuam abertas na unidade — contaminando o turno seguinte. No fluxo CONSUMA PONTO (balcão, QR público, grab-and-go), o fecho manual constante da sessão não é operacionalmente viável.

---

## 3. Base da Auditoria Anterior

- Branch de auditoria: `audit/sessao-consumo-turnos-001`
- Backend de referência: `backend/consuma-demo-freezy-happy-path-e2e-001` @ `6df9089`
- Frontend de referência: `ui/consuma-demo-freezy-app-pedidos-001` @ `114d604`
- Estados identificados: `ABERTA`, `AGUARDANDO_PAGAMENTO`, `ENCERRADA`, `EXPIRADA`
- Relações: 1:1 com FundoConsumo, N:1 com UnidadeAtendimento, mesa opcional, agrega Pedidos, impacta pré-fecho do Turno

---

## 4. Regra de Auto-Fecho

A regra central é:

> Se a sessão pertence a um contexto CONSUMA PONTO  
> **e** possui pelo menos um pedido processado  
> **e** todos os pedidos da sessão estão em estado terminal  
> **e** não existe pagamento pendente  
> **e** não existe ordem de pagamento activa  
> **e** não existe subpedido pendente obrigatório  
> → a sessão pode ser encerrada automaticamente pelo sistema.

---

## 5. Escopo: CONSUMA PONTO

Templates elegíveis:
- `CONSUMA_PONTO_V1`
- `CONSUMA_PONTO_QR`

Cenários cobertos:
- Pedido rápido (balcão)
- QR Público grab-and-go
- Qualquer fluxo com `templateCode` PONTO

---

## 6. Fora do Escopo

| O que **não** é tratado aqui |
|---|
| REST/KDS — sessão de mesa |
| QR Mesa REST |
| SERVICE / DELIVERY |
| Fecho forçado de turno |
| Fecho normal de turno (lógica não alterada) |
| Estorno / PDV / Caixa / Fiscal / KDS |
| Gateway de pagamento |
| Frontend / Cardápio Público / Acompanhamento Público |

---

## 7. Estados Considerados

| Estado | Elegível para auto-fecho |
|---|---|
| `ABERTA` | Sim, se pré-condições cumpridas |
| `AGUARDANDO_PAGAMENTO` | Sim, se pré-condições cumpridas |
| `ENCERRADA` | Não (idempotência — ignora) |
| `EXPIRADA` | Não (ignora) |

---

## 8. Pré-condições de Elegibilidade

1. Sessão existe
2. Status é `ABERTA` ou `AGUARDANDO_PAGAMENTO`
3. Sessão pertence a tenant com `templateCode` PONTO
4. Sessão tem pelo menos 1 pedido associado
5. Nenhum pedido em `CRIADO` ou `EM_ANDAMENTO`
6. Todos os pedidos não-cancelados têm `statusFinanceiro` != `NAO_PAGO` e != `PENDENTE_PAGAMENTO`
7. Nenhum pedido não-cancelado tem subpedido em `PENDENTE` ou `EM_PREPARACAO`
8. Nenhuma `OrdemPagamento` com status `AGUARDANDO_CONFIRMACAO` ligada à sessão

Se **todas** as condições forem verdadeiras → transicionar para `ENCERRADA`.

---

## 9. Pontos de Chamada

O `SessaoConsumoAutoClosureService.tryAutoCloseSessaoConsumo(Long sessaoId)` é chamado nos seguintes pontos:

| Classe | Ponto | Evento |
|---|---|---|
| `PedidoService` | `recalcularStatusPedido()` | Após pedido atingir `FINALIZADO` ou `CANCELADO` |
| `PedidoFinanceiroService` | `confirmarPagamentoPosPago()` | Após confirmação de pagamento pós-pago |
| `PedidoFinanceiroService` | `estornarPagamento()` | Após estorno concluído |

A regra está **centralizada** no service — não espalhada por fluxos.

---

## 10. Auditoria

Após encerramento bem-sucedido, regista via `OperationalEventLogService.logGenericForTenant()`:

| Campo | Valor |
|---|---|
| `eventType` | `SESSAO_CONSUMO_ENCERRADA` |
| `entityType` | `SESSAO_CONSUMO` |
| `entityId` | `sessaoConsumo.getId()` |
| `origem` | `SYSTEM` |
| `motivo` | `"Auto encerramento por conclusão de pedidos PONTO"` |
| `meta.reason` | `AUTO_CLOSURE_CONSUMA_PONTO` |
| `meta.old_status` | Estado anterior |
| `meta.new_status` | `ENCERRADA` |
| `meta.total_pedidos` | Quantidade de pedidos |

---

## 11. Idempotência

- Se a sessão já estiver `ENCERRADA` ou `EXPIRADA` → retorna imediatamente, sem save, sem auditoria duplicada.
- Se dois fluxos concorrentes tentarem fechar → o segundo encontra a sessão já encerrada e sai sem duplicar.
- Risco residual: em cenário de alta concorrência, sem lock optimista, pode haver uma janela de 2 writes consecutivos. Mitigado pelo check de estado no início do método.

---

## 12. Relação com Pré-Fecho do Turno

Após auto-fecho:
- A sessão fica com status `ENCERRADA`
- O pré-fecho do turno, ao contar `sessoesAbertas`, deixa de contabilizar esta sessão
- O fecho normal do turno fica possível se não houver outros bloqueios

**Esta fase NÃO fecha o turno automaticamente.** Apenas elimina o bloqueio indevido desta sessão.

---

## 13. Relação com Pagamento

- O auto-fecho **não altera** o status do pagamento
- O pagamento permanece `PAGO`
- A `OrdemPagamento` permanece `CONFIRMADA`
- Nenhum estorno, nenhum reembolso, nenhum movimento de caixa

---

## 14. Relação com OrdemPagamento

- Se existir `OrdemPagamento` com status `AGUARDANDO_CONFIRMACAO` → sessão **não encerra**
- Após confirmação da ordem, o evento dispara nova avaliação → se tudo liquidado, sessão encerra

---

## 15. Relação com FundoConsumo

- O método `SessaoConsumo.encerrar()` chama internamente `fundoConsumo.encerrar()` se o fundo existir
- O auto-fecho não cria extrato, não valida saldo separadamente
- O FundoConsumo é encerrado como parte do comportamento da entidade

---

## 16. Riscos Restantes

| Risco | Mitigação |
|---|---|
| Concorrência sem lock optimista | Check de estado no início; baixo risco em produção PONTO |
| Template null/ausente | Guard `if (template != null && !template.equals(...))` — sessão sem template passa a verificação e avalia normalmente |
| Sessões REST sem templateCode definido | Se templateCode for null, a sessão **não é bloqueada** pela regra REST; risco aceite — deve ser normalizado em fase futura |
| Scheduler de expiração em paralelo | Compatível: ambos fazem check de estado antes de agir |

---

## 17. Casos Futuros

- Auto-fecho para sessão `REST/KDS` com regra explícita (mesa vazia, tempo máximo)
- Lock optimista via `@Version` para eliminar risco de concorrência
- Evento de domínio assíncrono (`SessaoConsumoAutoFechadaEvent`) para desacoplar auditoria
- Migração de sessões abertas entre turnos (fase dedicada)
- Auto-fecho para SERVICE/DELIVERY quando aplicável
