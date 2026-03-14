# FASE 0 — ARQUITETURA CONGELADA
## Sistema de Restauração — Modelo baseado em Sessão de Consumo

**Data:** 7 de março de 2026  
**Versão:** 1.0  
**Estado:** Documento de referência oficial — gerado após análise comparativa entre o modelo conceitual (`modelo conceitual v2.txt`) e a implementação atual do código-fonte.

---

## 1. VISÃO GERAL DO MODELO DE DOMÍNIO

O sistema opera sobre o conceito central de **Sessão de Consumo** — cada evento de consumo no estabelecimento é representado por uma sessão independente, auditável e encerrada ao final.

A sessão:
- identifica o consumidor (identificado ou anónimo)
- agrupa todos os pedidos realizados
- está associada a um Fundo de Consumo obrigatório
- é vinculada opcionalmente a uma mesa física

A mesa **não é** a conta. A mesa **não controla** saldo. A mesa é apenas um contexto físico permanente.

O design visa suportar múltiplos cenários operacionais: restaurantes, bares, discotecas, eventos e festivais.

---

## 2. LISTA OFICIAL DE ENTIDADES DO SISTEMA

### 2.1 Entidades nucleares (núcleo operacional)

| Entidade | Localização no código | Responsabilidade |
|---|---|---|
| `SessaoConsumo` | `model/entity/SessaoConsumo.java` | Núcleo do sistema. Agrupa pedidos, fundo, cliente e mesa. |
| `FundoConsumo` | `model/entity/FundoConsumo.java` | Saldo financeiro pré-pago associado ao consumidor. |
| `TransacaoFundo` | `model/entity/TransacaoFundo.java` | Registo auditável de cada movimentação financeira do fundo. |
| `Pedido` | `model/entity/Pedido.java` | Solicitação de produtos vinculada à sessão. |
| `SubPedido` | `model/entity/SubPedido.java` | Divisão operacional do pedido por unidade de produção. |
| `ItemPedido` | `model/entity/ItemPedido.java` | Linha individual de produto dentro de um pedido. |
| `Produto` | `model/entity/Produto.java` | Item do catálogo com preço, categoria e disponibilidade. |
| `Cozinha` | `model/entity/Cozinha.java` | Unidade de produção (cozinha, bar, cafetaria, etc.). |
| `Mesa` | `model/entity/Mesa.java` | Contexto físico permanente. Nunca controla consumo ou saldo. |
| `Cliente` | `model/entity/Cliente.java` | Identificação opcional do consumidor via telefone e OTP. |

### 2.2 Entidades de suporte operacional (presentes na implementação, ausentes no modelo conceitual)

| Entidade | Localização no código | Responsabilidade |
|---|---|---|
| `UnidadeAtendimento` | `model/entity/UnidadeAtendimento.java` | Ponto de entrada de pedidos; agrega mesas e cozinhas. |
| `UnidadeDeConsumo` | `model/entity/UnidadeDeConsumo.java` | Ponto de consumo individual vinculado ao cliente. |
| `Atendente` | `model/entity/Atendente.java` | Operador que abre sessões e gere pedidos. |
| `Pagamento` | `model/entity/Pagamento.java` | Registo de transações com o gateway financeiro (AppyPay). |
| `QrCodeToken` | `model/entity/QrCodeToken.java` | Token QR com expiração e tipo (MESA, ENTREGA, PAGAMENTO). |
| `User` | `model/entity/User.java` | Utilizador autenticado do sistema (segurança). |

### 2.3 Entidades de auditoria e configuração

| Entidade | Localização no código | Responsabilidade |
|---|---|---|
| `PedidoEventLog` | `model/entity/PedidoEventLog.java` | Histórico de transições de estado do pedido. |
| `SubPedidoEventLog` | `model/entity/SubPedidoEventLog.java` | Histórico de transições de estado do subpedido. |
| `PagamentoEventLog` | `model/entity/PagamentoEventLog.java` | Histórico de eventos do gateway de pagamento. |
| `ConfiguracaoFinanceiraSistema` | `model/entity/ConfiguracaoFinanceiraSistema.java` | Parâmetros financeiros globais do sistema. |
| `ConfiguracaoFinanceiraEventLog` | `model/entity/ConfiguracaoFinanceiraEventLog.java` | Auditoria de alterações nas configurações financeiras. |

### 2.4 Enumerações relevantes

| Enum | Localização | Valores relevantes |
|---|---|---|
| `StatusSessaoConsumo` | `model/enums/` | `ABERTA`, `AGUARDANDO_PAGAMENTO`, `ENCERRADA` |
| `StatusPedido` | `model/enums/` | `CRIADO`, `EM_PREPARO`, `PRONTO`, `ENTREGUE`, `CANCELADO` |
| `StatusSubPedido` | `model/enums/` | `CRIADO`, `PENDENTE`, `EM_PREPARACAO`, `PRONTO`, `ENTREGUE`, `CANCELADO` |
| `TipoTransacaoFundo` | `model/enums/` | `CREDITO`, `DEBITO`, `ESTORNO` |
| `CategoriaProduto` | `model/enums/` | `ENTRADA`, `PRATO_PRINCIPAL`, `BEBIDA_ALCOOLICA`, etc. |
| `TipoPagamentoPedido` | `model/enums/` | `PRE_PAGO`, `POS_PAGO` |

> **Nota:** `CategoriaProduto` está implementada como enum, não como entidade com relação a `Cozinha`. Ver Ponto de Atenção #3.

---

## 3. RELAÇÕES ENTRE ENTIDADES

### 3.1 Relações do modelo conceitual — estado de conformidade

| Relação conceitual | Estado | Implementação real |
|---|---|---|
| `SessaoConsumo 1 — 1 FundoConsumo` | ⚠️ DIVERGÊNCIA | `FundoConsumo` está ligado a `Cliente` (1:1) ou a `tokenPortador`, não diretamente à `SessaoConsumo`. |
| `SessaoConsumo 1 — N Pedido` | ✅ CONFORME | `Pedido.sessaoConsumo` — `@ManyToOne`, `nullable = false`. |
| `Pedido 1 — N SubPedido` | ✅ CONFORME | `SubPedido.pedido` — `@ManyToOne`, `nullable = false`. |
| `Produto N — 1 CategoriaProduto` | ⚠️ DIVERGÊNCIA | `CategoriaProduto` é enum, não entidade. Não existe relação com `Cozinha` via categoria. |
| `CategoriaProduto N — 1 Cozinha` | ⚠️ DIVERGÊNCIA | Não implementada. Roteamento à cozinha é feito via `UnidadeAtendimento ↔ Cozinha`. |
| `SessaoConsumo N — 1 Mesa (opcional)` | ⚠️ DIVERGÊNCIA | `SessaoConsumo.mesa` está marcado como `nullable = false` — mesa é obrigatória na implementação. |
| `SessaoConsumo N — 1 Cliente (opcional)` | ✅ CONFORME | `SessaoConsumo.cliente` — `@ManyToOne`, sem `nullable = false`. |
| `FundoConsumo 1 — N TransacaoFundo` | ✅ CONFORME | `TransacaoFundo.fundoConsumo` — `@ManyToOne`, `nullable = false`. |

### 3.2 Relações adicionais identificadas na implementação

| Relação | Descrição |
|---|---|
| `UnidadeAtendimento N — N Cozinha` | Via tabela `unidade_cozinha`. Mecanismo real de roteamento operacional. |
| `UnidadeAtendimento 1 — N Mesa` | Mesas pertencem a uma unidade de atendimento. |
| `SubPedido N — 1 UnidadeAtendimento` | SubPedido referencia a unidade de origem. |
| `Pagamento N — 1 Pedido` | Rastreio de transações no gateway por pedido. |
| `Pagamento N — 1 FundoConsumo` | Rastreio de recargas no gateway por fundo. |
| `Mesa 1 — 1 QrCode (fixo)` | QR Code permanente da mesa, não da sessão. |
| `QrCodeToken N — 1 Mesa` | Tokens QR dinâmicos vinculados à mesa. |

---

## 4. FLUXOS OPERACIONAIS SUPORTADOS

### Análise por cenário

| Cenário | Suportado | Observação |
|---|---|---|
| Sessão anónima com QR Code | ✅ | `modoAnonimo = true`, `qrCodePortador` preenchido. |
| Sessão identificada com telefone | ✅ | `SessaoConsumo.cliente` com `telefoneVerificado` via OTP. |
| Sessão sem mesa | ⚠️ | Conceitualmente previsto. Implementação atual tem `mesa` como `nullable = false`. Bloqueante. |
| Sessão associada a mesa | ✅ | Relação `SessaoConsumo.mesa` funcional. |
| Múltiplas sessões associadas à mesma mesa | ✅ | Regra: uma mesa só pode ter UMA sessão com `status = ABERTA` por vez. Histórico de sessões conservado. |
| Recarga de fundo de consumo | ✅ | `TransacaoFundo` tipo `CREDITO` (denominado `RECARGA` no modelo conceitual). |
| Criação de pedidos a partir da sessão | ✅ | `Pedido.sessaoConsumo` obrigatório. |
| Divisão automática de pedidos em subpedidos por cozinha | ✅ | `SubPedido.cozinha` obrigatório; roteamento via `UnidadeAtendimento ↔ Cozinha`. |
| Débito do fundo após pedido | ✅ | `TransacaoFundo` tipo `DEBITO` com `pedidoId`. |
| Encerramento de sessão | ✅ | Ciclo: `ABERTA → AGUARDANDO_PAGAMENTO → ENCERRADA`. |
| Pagamento via gateway externo | ✅ | Entidade `Pagamento` com integração AppyPay (GPO/REF). Não previsto no modelo conceitual. |

---

## 5. PRINCÍPIOS ARQUITETURAIS ADOTADOS

### Verificação de conformidade

| Princípio | Estado | Evidência no código |
|---|---|---|
| Sessão de Consumo é a entidade central | ✅ CONFORME | `Pedido.sessaoConsumo` obrigatório; `SessaoConsumo` é o agregador de todos os pedidos. |
| Fundo de consumo é obrigatório por sessão | ⚠️ PARCIAL | `FundoConsumo` existe mas está ligado ao `Cliente`, não diretamente à `SessaoConsumo`. A associação é indireta. |
| Mesa não controla consumo e não possui saldo | ✅ CONFORME | `Mesa` não tem `FundoConsumo`. Status de ocupação é derivado via `SessaoConsumo`. |
| QR Code representa a sessão | ⚠️ DIVERGÊNCIA | `Mesa` possui `qrCode` permanente. `QrCodeToken` existe separado. `SessaoConsumo` usa `qrCodePortador` apenas no modo anónimo. QR Code não identifica univocamente a sessão. |
| Pedidos e transações financeiras pertencem à sessão | ✅ CONFORME | `Pedido → SessaoConsumo`; `TransacaoFundo → FundoConsumo → Cliente/Token`. |
| Sessões podem ser anónimas | ✅ CONFORME | `modoAnonimo = true`, fundo identificado por `tokenPortador`. |
| Modelo funciona em ambientes diversos | ✅ CONFORME | `UnidadeAtendimento` com `TipoUnidadeAtendimento` (RESTAURANTE, BAR, EVENTO, etc.). |

---

## 6. PONTOS DE ATENÇÃO IDENTIFICADOS

### PA-01 — DIVERGÊNCIA CRÍTICA: FundoConsumo não está vinculado à SessaoConsumo

**Descrição:**  
O modelo conceitual define `SessaoConsumo 1 — 1 FundoConsumo` com `FundoConsumo.sessaoId`. Na implementação, `FundoConsumo` está vinculado ao `Cliente` (`@OneToOne`) ou ao `tokenPortador` (modo anónimo). Não existe relação direta `FundoConsumo → SessaoConsumo`.

**Impacto:**  
- Impossibilidade de ter múltiplas sessões com fundos distintos para o mesmo cliente.
- O ciclo de vida do fundo não está acoplado ao ciclo de vida da sessão.
- O modelo de recarga e débito opera a nível de cliente/token, não a nível de sessão.

**Decisão necessária antes de prosseguir:**  
Definir formalmente se o `FundoConsumo` é por **sessão** ou por **cliente/portador**. As duas abordagens são arquiteturalmente válidas mas incompatíveis entre si.

---

### PA-02 — DIVERGÊNCIA CRÍTICA: Mesa é obrigatória na SessaoConsumo

**Descrição:**  
O modelo conceitual estabelece que "Uma sessão pode existir sem mesa" e "Mesa é apenas um contexto físico opcional". Na implementação, `SessaoConsumo.mesa` está mapeado com `nullable = false`, tornando a mesa obrigatória.

**Impacto:**  
- Sistema incapaz de criar sessões sem mesa (festivais, eventos sem lugares fixos, serviços de entrega, etc.).
- O cenário "sessão anónima com QR Code" sem mesa física está bloqueado na camada de persistência.

**Acção recomendada:**  
Alinhar com o modelo conceitual: alterar `nullable = false` para `nullable = true` em `SessaoConsumo.mesa`, ou documentar que a obrigatoriedade é uma decisão de negócio consciente que diverge do modelo.

---

### PA-03 — DIVERGÊNCIA ESTRUTURAL: CategoriaProduto é Enum, não Entidade

**Descrição:**  
O modelo conceitual define `CategoriaProduto` como uma entidade com `cozinhaId`, funcionando como mecanismo de roteamento de pedidos para cozinhas. Na implementação, `CategoriaProduto` é um enum simples sem qualquer relação com `Cozinha`.

**Impacto:**  
- O roteamento de produtos para cozinhas não é feito via categoria.
- A associação `Produto → Categoria → Cozinha` não existe.
- O roteamento real opera via `UnidadeAtendimento ↔ Cozinha` (many-to-many), o que é um mecanismo diferente e mais complexo.

**Acção recomendada:**  
Documentar explicitamente o mecanismo real de roteamento e actualizar o modelo conceitual para reflectir `CategoriaProduto` como enum + `UnidadeAtendimento ↔ Cozinha` como ponto de roteamento.

---

### PA-04 — AMBIGUIDADE: Localização e papel do QR Code

**Descrição:**  
O modelo conceitual afirma: "O QR Code representa a Sessão de Consumo" e `SessaoConsumo.qrCode` como campo principal. Na implementação existem três QR Codes distintos:
1. `Mesa.qrCode` — permanente, fixo à mesa física
2. `QrCodeToken.token` — dinâmico, com expiração, por tipo (MESA, ENTREGA, PAGAMENTO)
3. `SessaoConsumo.qrCodePortador` — apenas no modo anónimo

**Impacto:**  
- Não existe um QR Code único e exclusivo por sessão.
- O QR Code escaneado identifica a mesa, não a sessão activa.
- No modo identificado, não há QR Code na sessão.

**Acção recomendada:**  
Decidir formalmente: o QR Code é da **mesa** (permanente) ou da **sessão** (por evento de consumo). Actualizar o modelo conceitual com a decisão definitiva.

---

### PA-05 — NOMENCLATURA INCONSISTENTE: TipoTransacaoFundo

**Descrição:**  
O modelo conceitual define os tipos: `RECARGA`, `DEBITO_PEDIDO`, `ESTORNO`, `AJUSTE`.  
A implementação define: `CREDITO`, `DEBITO`, `ESTORNO`.

**Impacto:**  
- `RECARGA` → `CREDITO`: renomeado, mas funcionalmente equivalente.
- `DEBITO_PEDIDO` → `DEBITO`: nome simplificado; semanticamente próximo.
- `AJUSTE`: não implementado. Não há mecanismo de ajuste manual de saldo.

**Acção recomendada:**  
Alinhar nomenclatura ou actualizar o modelo conceitual. Avaliar se `AJUSTE` é necessário para o negócio.

---

### PA-06 — ENTIDADES NÃO PREVISTAS NO MODELO CONCEITUAL

**Descrição:**  
A implementação contém entidades não documentadas no modelo conceitual:

| Entidade | Justificativa provável |
|---|---|
| `UnidadeAtendimento` | Agrupa mesas e cozinhas; mecanismo de roteamento real. |
| `UnidadeDeConsumo` | Ponto de consumo individual do cliente. |
| `Atendente` | Operador do sistema; rastreio de abertura de sessão. |
| `Pagamento` | Integração com gateway AppyPay — não previsto no modelo. |
| `QrCodeToken` | Gestão dinâmica de tokens QR. |
| `ItemPedido` | Detalhe de linha de produto no pedido (esperado mas não documentado). |

**Acção recomendada:**  
Incorporar estas entidades na próxima versão do modelo conceitual.

---

### PA-07 — ESTADO DO SUBPEDIDO: Inconsistência menor de nomenclatura

**Descrição:**  
O modelo conceitual define o estado `CRIADO` como primeiro estado do `SubPedido`. A implementação inclui `CRIADO` e `PENDENTE` como estados iniciais distintos, sugerindo um passo adicional de transição não documentado.

**Impacto:** Baixo. Não bloqueia desenvolvimento, mas deve ser documentado.

---

## 7. DIAGRAMA DE RELAÇÕES (MODELO ACTUAL)

```
UnidadeAtendimento ──── N Cozinha (many-to-many)
       │
       1
       N
     Mesa ────── QrCodeToken
       │
       N
       │
SessaoConsumo ──── 1 ── (FundoConsumo via Cliente/Token) ── N ── TransacaoFundo
       │
       N
     Pedido ─────────────────────── N ── SubPedido ── 1 ── Cozinha
       │                                      │
       N                                      1
    ItemPedido                          UnidadeAtendimento
       │
       N
    Produto ── Enum(CategoriaProduto)
```

> O modelo acima representa a implementação real. Difere do modelo conceitual principalmente na ligação `FundoConsumo`.

---

## 8. CLASSIFICAÇÃO FINAL

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   MODELO CONSOLIDADO COM AJUSTES NECESSÁRIOS                 ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

**Fundamentação:**

O modelo conceitual está estruturalmente sólido e os seus princípios nucleares são claros e consistentes. A implementação está largamente alinhada com a visão de domínio.

No entanto, existem **duas divergências críticas** que devem ser resolvidas antes do início de qualquer nova fase de desenvolvimento:

1. **PA-01** — Relação `FundoConsumo ↔ SessaoConsumo` vs `FundoConsumo ↔ Cliente`: decisão de arquitectura com impacto directo no modelo de dados e nos fluxos financeiros.

2. **PA-02** — Mesa obrigatória na `SessaoConsumo`: contradiz o princípio fundamental de que a mesa é contexto físico opcional.

As restantes divergências (PA-03 a PA-07) são relevantes mas não bloqueantes para a estabilização inicial. Podem ser resolvidas progressivamente.

---

## 9. PRÓXIMOS PASSOS RECOMENDADOS (ANTES DE INICIAR FASE 1)

1. **Decisão formal** sobre PA-01: FundoConsumo por sessão ou por cliente/portador.
2. **Decisão formal** sobre PA-02: mesa obrigatória ou opcional na SessaoConsumo.
3. **Decisão formal** sobre PA-04: QR Code é da mesa ou da sessão.
4. Actualizar o modelo conceitual (`modelo conceitual v2.txt`) com as decisões tomadas.
5. Validação e aprovação do modelo conceitual actualizado.
6. Início da Fase 1 apenas após aprovação formal.

---

*Documento gerado na Fase 0 — Congelamento de Arquitectura.*  
*Referência: `docs/modelo conceitual v2.txt`*  
*Código analisado: `src/main/java/com/restaurante/model/`*
