# DOCUMENTAÇÃO DE INTEGRAÇÃO DE APIs
**Sistema de Restauração — KuendaGO**

Este documento estabelece o contrato oficial de integração para consumo do backend por parte dos clientes (Painel Administrativo, POS, KDS e Cliente Final via QR Ordering).

---

## 1. VISÃO GERAL DA API

### Descrição do Sistema
O backend foi concebido sobre uma **Arquitetura Modular Monolítica** orientada a Domínio (DDD) e eventos, providenciando endpoints REST para operações transacionais e Websockets para subscrição reativa. O coração do modelo orbita em torno de **Sessões de Consumo**, as quais retêm um estado financeiro derivado do consumo (Ledger append-only). O motor de pedidos isola o estado agrupando sub-pedidos operacionais (Cozinhas) para apurar o ciclo de vida final.

### Arquitetura REST
- Padrão **JSON** para request e response.
- Envelope padronizado (`ApiResponse<T>`) que confere predictibilidade: `status`, `message`, `data`.
- Rotas tipificadas segundo pluralização (ex: `/api/pedidos`, `/api/sessoes-consumo`).

### Arquitetura WebSocket
- Subscrição através de **STOMP** over WebSockets na rota de handshake `/api/ws`.
- Comunicação assíncrona orientada por canais temáticos (Cozinha, Atendente, Manager, Order).
- Envio unilateral: Backends notificam frontends. Requisições originadas do front-end seguem sempre a rota REST.

---

## 2. AUTENTICAÇÃO E SEGURANÇA

A estratégia de segurança baseia-se em tokens JWT gerados via Spring Security sem manutenção de estado de sessão (StateLess).

### Roles e Permissões
Todas as proteções seguem anotação de acesso Role-Based (RBAC):
- **`CLIENTE`**: acesso exclusivo ao escopo do utilizador corrente em Sessões Activas da sua titularidade, e injecção no Fundo de Consumo pessoal via OTP.
- **`ATENDENTE`**: permissão para alocar clientes, gerir sessões, enviar pedidos (POS).
- **`COZINHA`**: operações focadas num SubPedido (Mutações de Status em KDS).
- **`GERENTE` / `ADMIN`**: super-utilizadores capazes de intervir no catálogo (Produtos, Mesas), cancelar e liquidar o Ledger financeiro.

### Autenticação Flow (Cliente final — OTP)
1. **POST** `/api/auth/cliente/otp/solicitar`
   O cliente envia o `telefone`. O backend processa o disparo de um OTP (6 dígitos).
2. **POST** `/api/auth/cliente/otp/validar`
   Verifica-se via hash + temporalidade o OTP enviado.
   **Request**:
   ```json
   {
     "telefone": "923000000",
     "otp": "123456"
   }
   ```
   **Response**:
   ```json
   {
     "token": "eyJhb...",
     "expiresIn": 86400,
     "roles": ["CLIENTE"]
   }
   ```
   
### Autenticação Flow (Garçom / Dashboard)
1. **POST** `/api/auth/login`
   Entrada nativa via credenciais organizacionais (Password Hash).
   **Request**:
   ```json
   {
     "username": "admin_pos",
     "password": "changeme"
   }
   ```
   **Response** (Identica à emissão do token).

_Nota: Todo e qualquer consumo REST posterior deverá transportar o cabeçalho `Authorization: Bearer <Token>`._

---

## 3. DOCUMENTAÇÃO DOS ENDPOINTS REST PREVISTOS

### 3.1. Sessões de Consumo (`/sessoes-consumo`)
A cápsula envolvente de todas as ordens transaccionais dentro de uma mesa/local.

- **POST** `/sessoes-consumo`
  - Descrição: Abre uma sessão para uma mesa.
  - Roles: `ATENDENTE`, `GERENTE`, `ADMIN`
  - *Response:* `SessaoConsumoResponse` com Id e QR link.

- **POST** `/sessoes-consumo/cliente/qr/{token}`
  - Descrição: Fluxo de QR Ordering. O cliente emparelha à mesa lendo o token encriptado inserido na mesa física e resgata ou levanta a Sessão de Consumo.
  - Roles: `CLIENTE`
  
- **GET** `/sessoes-consumo/cliente/minha-sessao`
  - Descrição: Regressa os moldes visíveis da Sessão atrelada e ativa em nome do requisitante.
  - Roles: `CLIENTE`

- **PUT** `/sessoes-consumo/{id}/fechar`
  - Descrição: Encerra em definitivo a Mesa liberando-a, e avalia o Ledger se permite (Sem pendências negativas POS_PAGO).
  - Roles: `GERENTE`, `ADMIN`

### 3.2. Pedidos (`/pedidos`)
Agregação operacional gerindo fluxos contendo listagens de produtos por cozinha.

- **POST** `/pedidos`
  - Descrição: O POS/Atendente lança um pacote de produtos perante uma sessão (Se PRE_PAGO: debita fundo; Se POS_PAGO: Avalia limite).
  - Roles: `ATENDENTE`, `GERENTE`, `ADMIN`
  - *Payload*:
    ```json
    {
      "sessaoConsumoId": 25,
      "tipoPagamento": "PRE_PAGO",
      "itens": [ { "produtoId": 3, "quantidade": 2, "observacoes": "S/ gelo" } ]
    }
    ```

- **POST** `/pedidos/cliente`
  - Descrição: Cliente envia pedido circunscrito e confinado unicamente à própria sessão.
  - Roles: `CLIENTE`
  
- **PUT** `/pedidos/{id}/confirmar`
  - Descrição: Destranca um Pedido Recém Criado para cair nas mãos (vistas) dos painéis de cozinha (KDS).
  - Roles: `ATENDENTE`

- **PUT** `/pedidos/{id}/cancelar?motivo=xxx`
  - Descrição: Revogação coerciva do Request. Gera devolução se o Ledger abateu valores. 
  - Roles: `GERENTE`, `ADMIN`

### 3.3. Fundos de Consumo (`/fundos-consumo`)
Gestão da Digital Wallet vinculada a sessão.

- **POST** `/fundos-consumo/{fundoId}/recarregar`
  - Descrição: Injecção manual ou integrada de divisas num Fundo.
  - Roles: `ATENDENTE` (Caixeiro)
  - *Payload*: `{"valor": 5000, "metodo": "TPA"}`

- **GET** `/fundos-consumo/{id}/extrato`
  - Descrição: Exposição da pipeline do *Append Only* (Todas as mutações do Ledger).

### 3.4. Produtos e Cardápio (`/produtos`)
Catálogo mestre da praça alimentar.

- **GET** `/produtos`
  - Descrição: Enumera todos itens e cardápio dispostos para venda.
  - Roles: Aberto contanto que logado.

### 3.5. Mesas (`/mesas`)

- **GET** `/mesas/livres` e **GET** `/mesas/ocupadas`
  - Descrição: Polling state-of-play do salão operativo.
  - Roles: `ATENDENTE`
  
---

## 4. MODELOS DE DADOS E DTOs (PAYLOADS)

Estruturas-chave transitadas na Serialização.

**SessaoConsumoResponse**
```json
{
  "id": 1,
  "status": "ABERTA",
  "dataAbertura": "15-03-2026T14:32:00",
  "mesaId": 3,
  "referenciaMesa": "MESA 20",
  "telefoneCliente": "923000000", /* Condicional */
  "fundoConsumoId": 2,
  "saldoAtual": 4000
}
```

**PedidoResponse**
```json
{
  "id": 105,
  "numero": "PED-20260315-001",
  "status": "CRIADO",
  "statusFinanceiro": "PAGO",
  "tipoPagamento": "PRE_PAGO",
  "total": 1500,
  "itens": [
     {
       "produtoId": 12,
       "produtoNome": "Imperial",
       "quantidade": 2,
       "subtotal": 1500
     }
  ]
}
```

**SubPedidoResponse** (Fragmentos encaminhados à Cozinha)
```json
{
  "id": 40,
  "numero": "PED-20260315-001-1",
  "status": "EM_PREPARACAO",
  "cozinhaNome": "Fritadeira Central",
  "itens": [...]
}
```

---

## 5. NOTIFICAÇÕES WEBSOCKET (REALTIME)

Tudo flui baseando-se no envio unilateral (Server-Side Push) para Frontends Reactivos mitigando Pollings absurdos. A subscrição exige `STOMP`. O endpoint Base WebSocket é `/ws`.

### Tópicos Operacionais

| DESTINO FRONTAL | TOPIC | QUANDO É EMITIDO |
|---|---|---|
| **Cozinha (KDS)** | `/topic/cozinha/{cozinhaId}` | `CRIACAO` de SubPedido destinado àquele posto e Cancelamentos de última hora. |
| **P.O.S (Garçom)** | `/topic/atendente/unidade/{unidadeId}` | O Status do fluxo transitou (ex: um Prato passou de `EM_PREPARACAO` a `PRONTO`). Alerta para ir levantar. |
| **Painel (Gerente)**| `/topic/gerente/pedidos`, `/topic/gerente/alertas` | Um pedido excede os Limites configurados na unidade (Ex: Alarme de `PEDIDO_BLOQUEADO_POR_LIMITE`). |
| **Cliente Web App** | `/topic/pedido/{pedidoId}` | Mudança parcelar do ciclo de vida em tempo real até à finalização da ordem que ele abriu. |

### Exemplo de Payload WebSocket (Mudança Status)
```json
{
  "tipoAcao": "MUDANCA_STATUS",
  "id": 40,
  "numero": "PED-20260315-001-1",
  "pedidoId": 105,
  "numeroPedido": "PED-20260315-001",
  "statusAnterior": "CRIADO",
  "statusNovo": "PENDENTE",
  "cozinhaId": 2,
  "timestamp": "2026-03-15T15:00:15"
}
```

---

## 6. TRATAMENTO DE ERROS

Por padronagem, os HTTP Error Codes não são dissimulados no Status Code 200. São transparentes a RFC 7231 (ex: `400 BAD_REQUEST`, `404 NOT_FOUND`, `409 CONFLICT`).
Todos invariavelmente mapeados no corpo com a assinatura de objecto ErrorResponse:

```json
{
  "timestamp": "2026-03-15T10:00:00",
  "status": 400,
  "error": "Erro de negócio",
  "message": "Saldo insuficiente para realizar pedido",
  "path": "/api/pedidos/cliente",
  "validationErrors": null 
}
```
*Em caso de MethodArgumentNotValidException (Inputs Form), o dictionary (Map) `validationErrors` devolve chaves corrompidas com as violações legíveis.*

---

## 7. FLUXOS E DIAGRAMAS

### 7.1. Fluxo QR Ordering (Self-Service)
1. Cliente entra fisicamente e tira *Scan* do QR de uma mesa livre. O App extrai e repara num Token Encriptado HASH.
2. Frontend evoca Autenticação via SMS/OTP. Captura token JWT com perfil de `CLIENTE`.
3. Frontend injecta no POST `/sessoes-consumo/cliente/qr/{TokenLido}`.
   - O Backend verifica token físico. Abre nova Sessão. Cliente emparelha-se.
4. Apresenta o Menu (`/produtos`). O cliente escolhe itens.
5. Emite Request POST `/pedidos/cliente`. Backend verifica validade do `PRE_PAGO`, confronta Ledger e cria transacções. Confirmação instantânea por WebSocket de regresso avisando "Pronto para Levantamento/Entrega".
6. Acabou consumo? PUT `/sessoes-consumo/aguardar-pagamento` avisa a sala que ele vai-se embora (Gerando conta a pagar).

### 7.2. Fluxograma Painel POS (Staff Garçom)
1. Garçom localiza Mesa Livre visualmente via Polling/Listing de mesas.
2. Clica "Abrir Sessão": POST `/sessoes-consumo`.
3. Lança Pedido: POST `/pedidos` em conta local de regime de `POS_PAGO`.
4. Os itens sofrem dispersão baseada em *Categorias*, recaindo num KDS Grill e num KDS Bar em forma fraccionada de `Sub-Pedidos`.
5. WebSockets piscam no ecrã de cada respectiva `Cozinha/Barra`.
6. Finalização: Pagamento no Terminal Virtual TPA, gerando POST de entrada no `.adicionarFundos()` manual, para culminar num PUT em `/fechar`.

---

## 8. MODELO CONCEPTUAL GERAL
- Uma **Sessão Consumo** encapsula temporalmente uma **Mesa**. E obriga o vinculo vitalício 1:1 a um **Fundo de Consumo** (Virtual Wallet).
- O **Fundo Consumo** é meramente uma fotografia abstracta. Todo o peso cai sobre logs do **TransacaoFundo**.
- Um **Pedido** transita em conjunto num aglomerado pai que totaliza Financeiramente o acto. Enquanto os filhos vitais, os **Sub-Pedidos**, dividem o labor da praça para instanciar cozeduras. 
- Sendo a mudança de estado dos KDS (Cozinheiros) sobre o **SubPedido** o espelho que calcula e deriva do status Final do **Pedido** Pai nas visões Macro de Restauração.
