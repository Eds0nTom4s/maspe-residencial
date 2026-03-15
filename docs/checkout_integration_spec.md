# Especificação de Integração de Checkout (Backend ↔ Frontend)

## Visão Geral do Motor Financeiro

Este documento define as diretrizes e endpoints necessários para o frontend realizar a finalização de checkout no Sistema de Restauração. 

**O princípio fundamental e inegociável da integração é: BACKEND FIRST.**
O frontend **nunca** calcula nem envia valores finais de pagamento. O backend é a fonte absoluta da verdade para preços, saldos, descontos e tributações. 

### Regras de Negócio do Checkout

1. **O frontend nunca decide valores:** Os pedidos só transportam IDs de produtos e quantidades. O backend consulta o catálogo em tempo-real para calcular o subtotal.
2. **Duplo modelo de sessão:**
    - `PRE_PAGO` (Padrão para Clientes/Menu QR): Cada produto pedido é debitado instantaneamente do `FundoConsumo` da sessão. Se não houver saldo suficiente, a transação falha.
    - `POS_PAGO` (Opcional gerencial): A fatura acumula (Estado: `NAO_PAGO`) e o checkout exige liquidação explícita posterior. Apenas Atendentes, Gerentes e Admins operam nisto.
3. **Idempotência Garantida:** Uma transação `PRE_PAGO` descontada não é descontada novamente. Pagamentos confirmados não podem ser repagos.
4. **Resumo Automático de Estoque:** Uma vez que o Pedido transita para `EM_ANDAMENTO`, ou é pago em Pré-Pago, o tracking financeiro já foi consolidado. O checkout de encerramento resume-se em certificar que tudo foi pago (para Pós-Pago) e libertar a mesa.

---

## 1. Modelação do Fundo de Consumo (Pré-Pago Digital)

No fluxo de cliente via QR Code padrão, operar um Pedido confunde-se com o Checkout, dado que se exige Pré-Pagamento. É fulcral demonstrar o saldo antes.

### Consultar Fundo do Cliente Logado
Retorna o saldo livre associado à sessão do cliente. Deve ser invocado antes de submeter um Pedido para validação primária na interface.

**ENDPOINT:** `GET /api/fundos/{qrCodeSessao}/saldo` (QR Code da Sessão é originado na inicialização da Sessão)  
**HEADERS:** `Authorization: Bearer <JWT>`  
**REQUER ROLE:** `CLIENTE` (ou Role Admin se consultado pelo staff)  

**HTTP STATUS:**
- `200 OK`: Sessão encontrada, retorna Decimal do saldo.
- `401 UNAUTHORIZED`: Cliente não autenticado.

**RESPONSE DE EXEMPLO:**
```json
{
  "success": true,
  "message": "Saldo consultado",
  "data": 15000.00
}
```

---

## 2. Motor de Pedido — O "Pagar Agora" do Cliente

O Cliente (QR Ordering) está sempre condicionado a gerar Pedidos com pagamento antecipado em contexto associado.

### Criar Pedido (Checkout Pré-Pago)

**ENDPOINT:** `POST /api/pedidos/cliente`  
**HEADERS:** `Authorization: Bearer <JWT>`  

**REQUEST BODY:**
```json
{
  "sessaoConsumoId": 14,
  "itens": [
    {
      "produtoId": 2,
      "quantidade": 1
    }
  ],
  "tipoPagamento": "PRE_PAGO",
  "observacoes": "Sem cebola"
}
```

> **IMPORTANTE FRISAR:** O payload *NÃO* reflete valor de moeda. O Request apenas sinaliza Produtos x Quantidade. O motor Backend `PedidoFinanceiroService` iterará sobre os registos do catálogo, calculará o apuramento total e transicionará o débito em Fundo (após validar se o Saldo > Total do Pedido).

**HTTP STATUS COMPORTAMENTAIS:**
- `201 CREATED`: O pedido foi processado, o saldo debitado com sucesso do FundoConsumo e foi encaminhado para as Cozinhas devidas.
- `400 BAD REQUEST`: Erro estrutural, produto desativado, ou violação de Restrição (ex: `Saldo insuficiente na carteira pré-paga para completar este pedido`). Exibir `ApiResponse.message`.
- `401 UNAUTHORIZED`: JWT ausente ou expirado.

---

## 3. O Checkout Físico (Pós-Pago / Operação Atendentes)

Quando a operação é servida convencionalmente por Atendentes com tablets (`POS_PAGO`), o fluxo encerra-se iterando pagamentos parciais e liquidando a mesa.

### 3.1 Solicitar Conta (Aguardar Pagamento)

**ENDPOINT:** `PUT /api/sessoes-consumo/cliente/minha-sessao/aguardar-pagamento` (Cliente via QR) ou `PUT /api/sessoes-consumo/{id}/aguardar-pagamento` (Staff)  
**OBJETIVO:** Transita a Sessão de ABERTA para AGUARDANDO_PAGAMENTO. A partir disto, é bloqueada a adição de novos itens/pedidos à conta. O garçom receberá notificação ou aviso visual da intenção de liquidação.

### 3.2 Liquidar Faturas e Checkout Em Massa (Staff)

**ENDPOINT:** `POST /api/sessoes-consumo/{id}/pos-pago/liquidar` (Parâmetro `metodoPagamento` injetável dependendo do implementador final)  
**OBJETIVO:** O Atendente/Gerente invoca esta via para pagar automaticamente em massa as faturas emabertas `NAO_PAGOS` no cenário Pós-Pago, o que invoca o `AuditoriaFinanceiraService` para rastro transacional, encerra as contas pendentes e tranca a Mesa libertando a Sessão para o status `ENCERRADA`.

**HEADERS:** `Authorization: Bearer <JWT_ATENDENTE>`

**HTTP STATUS COMPORTAMENTAIS:**
- `200 OK`: A sessão confirmou todos os pagamentos em atraso `NAO_PAGO` gerou o log de auditoria e mudou o status da Sessão para `ENCERRADA`. A Mesa está `DISPONIVEL` novamente.
- `400 BAD REQUEST`: Tentar invocar liquidação sobre Sessão PRÉ_PAGADA ou operação em sessão Inválida.
- `409 CONFLICT`: Existem Itens na cozinha ainda sendo confeccionados (`EM_ANDAMENTO`). O sistema aborta o Checkout para salvaguardar a coerência e perda de track.

---

## 4. Referência de Códigos HTTP Padrão

O frontend deve basear o seu fluxo da interface de acordo com os códigos de estado HTTP padronizados de resposta:

| Status Code | Significado Técnico | Ação Esperada no Frontend |
| ----------- | ----------- | ------------------------- |
| `200 OK` | Operações de Mutação/Consulta c/ sucesso. | Redirecionamento de Sucesso (Checkout concluído) ou Renderização do Saldo atual. |
| `201 CREATED` | Recurso gerado com sucesso na DB. | Exibir ticket do pedido ou confirmação de transação aceite. |
| `400 BAD REQUEST` | Quebras de regra de negócio (`BusinessException`) ou Payload falhado. | Ler a mensagem de erro fornecida no `data.message` e renderizar notificação Toast ou Banner de aviso formatado (Ex: "Saldo Esgotado", "Produto Suspenso"). |
| `401 UNAUTHORIZED` | Ausência de Autenticação. | Purgar credenciais antigas em storage, reencaminhar forçosamente para Autenticação OTP. |
| `403 FORBIDDEN` | Cliente a tentar interceptar operações de Atendente/Gerente. | Esconder elementos protegidos se visíveis, ou apresentar falha genérica de Privilégios negados. |
| `404 NOT FOUND` | QR Code escaneado não aponta para uma mesa existente. | Recarregar Interface informando "Scanner inválido". |
| `409 CONFLICT` | Race-conditions (Conflito de estado). | Pedir confirmação interativa: "Aviso: Existem pratos não servidos, tem a certeza que quer liquidar sem os cancelar?". |
| `500 INTERNAL SERVER ERROR` | Quebra na Camada Lógica inferior do Sistema / Desconexão (Banco e afins). | Indicar "Ocorreu um erro na rede/servidor." e acionar tracking analítico de falhas à equipa. |

---

## 5. Integração WebSocket (Notificação Financeira em Tempo-Real)

Durante o tempo de vida do pedido ou liquidação física efetuada por um Atendente, o Frontend deve estar apto a escutar o fluxo real passivamente.

**TÓPICO WEBSOCKET GLOBAL DE STATUS DOS PEDIDOS:** `/topic/pedidos`  

**CARGAS DE EXEMPLO PREVISTAS:**
```json
{
  "evento": "pedido_pago",
  "payload": {
    "pedidoId": 45,
    "numeroFormatado": "PED-2026-0045",
    "statusFinanceiro": "PAGO",
    "horaPagamento": "2026-03-15T10:05:43"
  }
}
```

O Frontend deverá escutar passivamente as mutações. Sempre que uma flag transitar para `PAGO` (em caso de pagamento por intermédio externo sem ser no dispositivo do user) ou `CANCELADO`, a Interface (como o Carrinho na App Móvel) deverá sofrer re-render (para espelhar que já está saldado) ou subtrair o valor/reverter a listagem de pendentes.

*Em caso onde não houver WebSockets implementados temporalmente, efetuar Polling ao Recarregar Manual ou chamar os endpoints listados (`/sessoes-consumo`, `/fundos/{id}/saldo`, `/pedidos/cliente`) resolve as dependências financeiras com total coerência para o Backend.*
