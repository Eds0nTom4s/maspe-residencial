# Documento de Integração — Painel Administrativo

**Sistema:** MASPE Residencial — Sistema de Restauração  
**Data:** 2026-03-16  
**Versão:** 1.0.0  
**Base URL:** `http://localhost:8080/api`

---

## 1. Visão Geral do Painel Administrativo

O Painel Administrativo é a interface operacional do sistema para uso por:

| Perfil     | Acesso Principal                               |
|------------|------------------------------------------------|
| ADMIN      | Configurações, usuários, relatórios, full access |
| GERENTE    | Sessões, pedidos, financeiro, auditoria          |
| ATENDENTE  | Mesas, sessões, pedidos, sub-pedidos             |
| COZINHA    | Fila de sub-pedidos da sua cozinha               |

---

## 2. Autenticação de Operadores

### Endpoint de Login

```
POST /api/auth/admin/login
Content-Type: application/json
```

**Request:**
```json
{
  "telefone": "+244923000001",
  "senha": "senha123"
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "nome": "João Atendente",
    "telefone": "+244923000001",
    "tipoUsuario": "ATENDENTE",
    "accessToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

**Erros Possíveis:**
- `401 Unauthorized` — Credenciais inválidas
- `403 Forbidden` — Conta desativada

### Uso do Token

Todas as requisições protegidas devem incluir o header:
```
Authorization: Bearer {accessToken}
```

---

## 3. Módulos do Painel — APIs por Funcionalidade

### 3.1 MÓDULO: Dashboard Operacional

> ⚠️ **ATENÇÃO:** Os endpoints de dashboard existem mas retornam dados mockados. Precisam ser reimplementados antes do uso em produção.

| # | Método | Endpoint               | Roles                          | Descrição                   |
|---|--------|------------------------|--------------------------------|-----------------------------|
| 1 | GET    | `/dashboard/stats`     | ADMIN, GERENTE, ATENDENTE      | Estatísticas gerais         |
| 2 | GET    | `/dashboard/activity`  | ADMIN, GERENTE, ATENDENTE      | Atividades recentes         |
| 3 | GET    | `/dashboard/top-products` | ADMIN, GERENTE              | Produtos mais vendidos      |
| 4 | GET    | `/sessoes-consumo/abertas` | ATENDENTE, GERENTE, ADMIN  | Sessões abertas (real ✅)   |
| 5 | GET    | `/mesas/ocupadas`      | Autenticado                    | Mesas ocupadas (real ✅)    |
| 6 | GET    | `/mesas/disponiveis`   | Autenticado                    | Mesas disponíveis (real ✅) |
| 7 | GET    | `/pedidos/ativos`      | Autenticado                    | Pedidos em andamento (real ✅) |

**Resposta de `GET /dashboard/stats`:**
```json
{
  "data": {
    "totalPedidosHoje": 25,
    "pedidosPendentes": 5,
    "receitaHoje": 1250.50,
    "clientesAtivos": 12
  }
}
```

---

### 3.2 MÓDULO: Gestão de Sessões

**Fluxo de Sessão:**
```
[ABRIR SESSÃO] → ABERTA → [PEDIDO CONTA] → AGUARDANDO_PAGAMENTO → [FECHAR] → ENCERRADA
                    ↓
                [FECHAR DIRETO] → ENCERRADA
```

| # | Método | Endpoint                                    | Roles                     | Descrição                      |
|---|--------|---------------------------------------------|---------------------------|--------------------------------|
| 1 | GET    | `/sessoes-consumo/abertas`                  | ATENDENTE, GERENTE, ADMIN | Sessões abertas                |
| 2 | GET    | `/sessoes-consumo/{id}`                     | Autenticado               | Detalhes da sessão             |
| 3 | GET    | `/sessoes-consumo/mesa/{mesaId}/ativa`      | Autenticado               | Sessão ativa de uma mesa       |
| 4 | GET    | `/sessoes-consumo/mesa/{mesaId}/historico`  | Autenticado               | Histórico de sessões           |
| 5 | POST   | `/sessoes-consumo`                          | ATENDENTE, GERENTE, ADMIN | Abrir nova sessão              |
| 6 | PUT    | `/sessoes-consumo/{id}/aguardar-pagamento`  | ATENDENTE, GERENTE, ADMIN | Sinalizar aguardando pagamento |
| 7 | PUT    | `/sessoes-consumo/{id}/fechar`              | ATENDENTE, GERENTE, ADMIN | Encerrar sessão                |

**Request — Abrir Sessão (`POST /sessoes-consumo`):**
```json
{
  "mesaId": 5,
  "tipoSessao": "PRESENCIAL",
  "observacoes": "Mesa para 4 pessoas"
}
```

**Response — SessaoConsumoResponse:**
```json
{
  "success": true,
  "data": {
    "id": 42,
    "mesaId": 5,
    "mesaReferencia": "Mesa 12",
    "status": "ABERTA",
    "qrCodeSessao": "uuid-do-qrcode",
    "tipoSessao": "PRESENCIAL",
    "createdAt": "2026-03-16T14:00:00"
  }
}
```

---

### 3.3 MÓDULO: Gestão de Mesas

**Status das Mesas:** O status é **derivado** — não persistido no banco. Uma mesa está OCUPADA se e somente se existe uma `SessaoConsumo` com status `ABERTA` para ela.

| # | Método | Endpoint                              | Roles       | Descrição                       |
|---|--------|---------------------------------------|-------------|---------------------------------|
| 1 | GET    | `/mesas`                              | Autenticado | Todas as mesas com status       |
| 2 | GET    | `/mesas/disponiveis`                  | Autenticado | Mesas disponíveis               |
| 3 | GET    | `/mesas/ocupadas`                     | Autenticado | Mesas ocupadas                  |
| 4 | GET    | `/mesas/{id}`                         | Autenticado | Detalhes da mesa                |
| 5 | POST   | `/mesas`                              | ADMIN       | Criar mesa                      |
| 6 | PUT    | `/mesas/{id}/ativar`                  | ADMIN       | Ativar mesa                     |
| 7 | PUT    | `/mesas/{id}/desativar`               | ADMIN       | Desativar (proibido se ocupada) |

**Response — MesaResponse:**
```json
{
  "id": 5,
  "referencia": "Mesa 12",
  "qrCode": "mesa-qr-abc123",
  "ativa": true,
  "statusOperacional": "OCUPADA",
  "sessaoAtiva": {
    "sessaoId": 42,
    "status": "ABERTA"
  }
}
```

---

### 3.4 MÓDULO: Gestão de Pedidos

**Fluxo de Pedido:**
```
CRIADO → [confirmar] → EM_ANDAMENTO → [todos subpedidos entregues] → FINALIZADO
       ↓                          ↓
   [cancelar]               [cancelar]
       ↓                          ↓
    CANCELADO                CANCELADO
```

| # | Método | Endpoint                                  | Roles                     | Descrição                     |
|---|--------|-------------------------------------------|---------------------------|-------------------------------|
| 1 | GET    | `/pedidos/ativos`                         | Autenticado               | Pedidos ativos                |
| 2 | GET    | `/pedidos/status/{status}`                | Autenticado               | Por status                    |
| 3 | GET    | `/pedidos/sessao-consumo/{id}`            | ATENDENTE, GERENTE, ADMIN | Pedidos de uma sessão         |
| 4 | GET    | `/pedidos/{id}`                           | Autenticado               | Detalhes do pedido            |
| 5 | POST   | `/pedidos`                                | ATENDENTE, GERENTE, ADMIN | Criar pedido                  |
| 6 | PUT    | `/pedidos/{id}/confirmar`                 | ATENDENTE, GERENTE, ADMIN | Confirmar → cozinha           |
| 7 | PUT    | `/pedidos/{id}/cancelar?motivo=`          | GERENTE, ADMIN            | Cancelar com motivo           |
| 8 | PUT    | `/pedidos/{id}/confirmar-pagamento`       | GERENTE, ADMIN            | Confirmar pagamento pós-pago  |
| 9 | PUT    | `/pedidos/{id}/fechar`                    | ATENDENTE, GERENTE, ADMIN | Fechar conta                  |

**Request — Criar Pedido (`POST /pedidos`):**
```json
{
  "sessaoConsumoId": 42,
  "tipoPagamento": "PRE_PAGO",
  "itens": [
    {
      "produtoId": 10,
      "quantidade": 2,
      "observacoes": "Sem cebola"
    }
  ]
}
```

> **Tipos de Pagamento:**
> - `PRE_PAGO` — Débita do Fundo de Consumo imediatamente
> - `POS_PAGO` — Requer autorização de GERENTE/ADMIN; pago na saída

---

### 3.5 MÓDULO: Gestão de Cozinha (KDS)

**Fluxo de Sub-Pedido:**
```
CRIADO → [confirmar pedido] → PENDENTE → [assumir] → EM_PREPARACAO → [pronto] → PRONTO → [entregar] → ENTREGUE
```

| # | Método | Endpoint                            | Roles                     | Descrição                      |
|---|--------|-------------------------------------|---------------------------|--------------------------------|
| 1 | GET    | `/subpedidos/cozinha/{id}/ativos`   | Autenticado               | Fila da cozinha                |
| 2 | GET    | `/subpedidos/cozinha/{id}/prontos`  | Autenticado               | Prontos para entregar          |
| 3 | GET    | `/subpedidos/atrasados?minutosAtraso=30` | Autenticado         | Sub-pedidos com atraso         |
| 4 | GET    | `/subpedidos/kpi/tempo-medio`       | Autenticado               | KPI tempo médio por cozinha    |
| 5 | PUT    | `/subpedidos/{id}/assumir`          | COZINHA, GERENTE, ADMIN   | Assumir (PENDENTE→EM_PREPARACAO) |
| 6 | PUT    | `/subpedidos/{id}/marcar-pronto`    | COZINHA, GERENTE, ADMIN   | Marcar pronto                  |
| 7 | PUT    | `/subpedidos/{id}/marcar-entregue`  | ATENDENTE, GERENTE, ADMIN | Marcar entregue                |
| 8 | PUT    | `/subpedidos/{id}/cancelar?motivo=` | GERENTE, ADMIN            | Cancelar sub-pedido            |
| 9 | GET    | `/cozinhas`                         | Autenticado               | Listar cozinhas                |
| 10| GET    | `/cozinhas/ativas`                  | Autenticado               | Cozinhas ativas                |

**Response — SubPedidoResponse:**
```json
{
  "id": 99,
  "pedidoId": 55,
  "numeroPedido": "PED-2026-0055",
  "cozinhaId": 1,
  "nomeCozinha": "Cozinha Principal",
  "status": "PENDENTE",
  "itens": [
    {
      "produtoNome": "Frango Grelhado",
      "quantidade": 2,
      "observacoes": "Sem molho"
    }
  ],
  "recebidoEm": "2026-03-16T14:10:00",
  "tempoPreparacaoMinutos": null
}
```

---

### 3.6 MÓDULO: Gestão de Fundo de Consumo

O Fundo de Consumo é identificado pelo `qrCodeSessao` (token UUID gerado na SessaoConsumo).

| # | Método | Endpoint                          | Roles             | Descrição                       |
|---|--------|-----------------------------------|-------------------|---------------------------------|
| 1 | GET    | `/fundos/{token}`                 | ATENDENTE, GERENTE, ADMIN | Consultar fundo         |
| 2 | GET    | `/fundos/{token}/saldo`           | ATENDENTE, GERENTE, ADMIN | Consultar saldo         |
| 3 | GET    | `/fundos/{token}/historico`       | GERENTE, ADMIN    | Histórico de transações         |
| 4 | GET    | `/fundos/sessao/{sessaoId}`       | GERENTE, ADMIN    | Fundo por sessão                |
| 5 | POST   | `/fundos/{token}/recarregar`      | GERENTE, ADMIN    | Recarregar manualmente          |

**Request — Recarregar Fundo:**
```json
{
  "valor": 5000.00,
  "observacoes": "Recarga balcão - cliente pagou em dinheiro"
}
```

**Response — TransacaoFundoResponse:**
```json
{
  "id": 201,
  "tipo": "CREDITO",
  "valor": 5000.00,
  "saldoAnterior": 2000.00,
  "saldoNovo": 7000.00,
  "observacoes": "Recarga balcão",
  "createdAt": "2026-03-16T14:15:00"
}
```

---

### 3.7 MÓDULO: Gestão de Produtos (Cardápio)

| # | Método | Endpoint                              | Roles           | Descrição                        |
|---|--------|---------------------------------------|-----------------|----------------------------------|
| 1 | GET    | `/produtos`                           | Autenticado     | Listar disponíveis               |
| 2 | GET    | `/produtos/categoria/{categoria}`     | Autenticado     | Por categoria                    |
| 3 | GET    | `/produtos/buscar?nome=`              | Autenticado     | Buscar por nome                  |
| 4 | POST   | `/produtos`                           | GERENTE, ADMIN  | Criar produto                    |
| 5 | PUT    | `/produtos/{id}`                      | GERENTE, ADMIN  | Atualizar produto                |
| 6 | PATCH  | `/produtos/{id}/disponibilidade?disponivel=true` | GERENTE, ADMIN | Ligar/desligar |
| 7 | DELETE | `/produtos/{id}`                      | GERENTE, ADMIN  | Desativar (soft delete)          |

**Categorias disponíveis** (enum `CategoriaProduto`):
- `ENTRADA`, `PRATO_PRINCIPAL`, `SOBREMESA`, `BEBIDA`, `ACOMPANHAMENTO`, `OUTROS`

**Request — Criar/Atualizar Produto:**
```json
{
  "nome": "Frango Grelhado",
  "descricao": "Frango grelhado com batata frita",
  "preco": 1500.00,
  "categoria": "PRATO_PRINCIPAL",
  "cozinhaId": 1,
  "disponivel": true,
  "codigo": "FRG-001"
}
```

---

### 3.8 MÓDULO: Gestão de Operadores

| # | Método | Endpoint                     | Roles  | Descrição                       |
|---|--------|------------------------------|--------|---------------------------------|
| 1 | GET    | `/usuarios`                  | ADMIN  | Listar (paginado)               |
| 2 | GET    | `/usuarios/{id}`             | ADMIN  | Detalhes do operador            |
| 3 | GET    | `/usuarios/permissoes`       | ADMIN  | Roles disponíveis               |
| 4 | POST   | `/usuarios`                  | ADMIN  | Criar operador                  |
| 5 | PUT    | `/usuarios/{id}`             | ADMIN  | Atualizar dados                 |
| 6 | PATCH  | `/usuarios/{id}/ativar`      | ADMIN  | Ativar conta                    |
| 7 | PATCH  | `/usuarios/{id}/desativar`   | ADMIN  | Desativar conta                 |
| 8 | PATCH  | `/usuarios/{id}/senha`       | ADMIN  | Alterar senha                   |

**Request — Criar Operador:**
```json
{
  "username": "joao.atendente",
  "nome": "João Atendente",
  "telefone": "+244923000001",
  "senha": "senha_segura_123",
  "email": "joao@restaurante.com",
  "role": "ATENDENTE"
}
```

---

### 3.9 MÓDULO: Configurações Financeiras

| # | Método | Endpoint                                        | Roles  | Descrição                      |
|---|--------|-------------------------------------------------|--------|--------------------------------|
| 1 | GET    | `/configuracoes-financeiras`                    | ADMIN  | Configuração atual             |
| 2 | GET    | `/configuracoes-financeiras/pos-pago/status`    | ATENDENTE, GERENTE, ADMIN | Status pós-pago |
| 3 | PUT    | `/configuracoes-financeiras/pos-pago/ativar?motivo=` | ADMIN | Ativar pós-pago          |
| 4 | PUT    | `/configuracoes-financeiras/pos-pago/desativar?motivo=` | ADMIN | Desativar pós-pago    |
| 5 | PUT    | `/configuracoes-financeiras/pos-pago/limite?novoLimite=` | ADMIN | Alterar limite      |
| 6 | PUT    | `/configuracoes-financeiras/valor-minimo?novoValor=` | ADMIN | Alterar valor mínimo     |

---

### 3.10 MÓDULO: Auditoria

| # | Método | Endpoint                                       | Roles           | Descrição                     |
|---|--------|------------------------------------------------|-----------------|-------------------------------|
| 1 | GET    | `/auditoria/acoes?limite=50&tipo=&operador=&inicio=&fim=` | GERENTE, ADMIN | Ações auditadas com filtros |
| 2 | GET    | `/auditoria/estatisticas`                      | GERENTE, ADMIN  | Contadores por tipo            |
| 3 | GET    | `/auditoria/modulos`                           | GERENTE, ADMIN  | Tipos de evento disponíveis   |

---

## 4. Eventos WebSocket

O sistema possui WebSocket configurado. Os seguintes tópicos devem ser utilizados pelo painel para atualizações em tempo real:

| Tópico                            | Gatilho                                           | Consumidores         |
|-----------------------------------|---------------------------------------------------|----------------------|
| `/topic/sessoes`                  | Abertura/fechamento de sessão                     | Dashboard, Mesas     |
| `/topic/pedidos`                  | Criação, confirmação, cancelamento de pedido      | Painel de pedidos    |
| `/topic/subpedidos/cozinha/{id}`  | Novo sub-pedido, mudança de status                | KDS (cozinha)        |
| `/topic/mesas`                    | Mudança de status de mesa (ocupada/disponível)    | Mapa de mesas        |

**Configuração do cliente WebSocket:**
```javascript
const socket = new SockJS('/api/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
  stompClient.subscribe('/topic/subpedidos/cozinha/1', (msg) => {
    const subpedido = JSON.parse(msg.body);
    // Atualizar fila da cozinha
  });
});
```

---

## 5. Padrão de Resposta da API

### Resposta de Sucesso
```json
{
  "success": true,
  "message": "Descrição da operação",
  "data": { ... }
}
```

### Resposta de Erro
```json
{
  "success": false,
  "message": "Descrição do erro",
  "data": null
}
```

### HTTP Status Codes

| Código | Significado                               |
|--------|-------------------------------------------|
| 200    | Operação realizada com sucesso            |
| 201    | Recurso criado com sucesso                |
| 400    | Dados inválidos na requisição             |
| 401    | Token ausente ou inválido                 |
| 403    | Permissão insuficiente para a operação    |
| 404    | Recurso não encontrado                    |
| 409    | Conflito de estado (ex: mesa já ocupada)  |
| 422    | Regra de negócio violada                  |
| 500    | Erro interno do servidor                  |

---

## 6. Regras de Autorização por Módulo

| Módulo                     | ADMIN | GERENTE | ATENDENTE | COZINHA |
|----------------------------|-------|---------|-----------|---------|
| Dashboard                  | ✅    | ✅      | ✅ (limitado) | ❌  |
| Sessões — leitura          | ✅    | ✅      | ✅        | ❌      |
| Sessões — operação         | ✅    | ✅      | ✅        | ❌      |
| Mesas — leitura            | ✅    | ✅      | ✅        | ✅      |
| Mesas — gestão             | ✅    | ❌      | ❌        | ❌      |
| Pedidos — leitura          | ✅    | ✅      | ✅        | ✅      |
| Pedidos — operação         | ✅    | ✅      | ✅        | ❌      |
| Pedidos — cancelamento     | ✅    | ✅      | ❌        | ❌      |
| SubPedidos — cozinha       | ✅    | ✅      | ❌        | ✅      |
| SubPedidos — entrega       | ✅    | ✅      | ✅        | ❌      |
| Fundo — consulta           | ✅    | ✅      | ✅        | ❌      |
| Fundo — recarga            | ✅    | ✅      | ❌        | ❌      |
| Produtos — leitura         | ✅    | ✅      | ✅        | ✅      |
| Produtos — gestão          | ✅    | ✅      | ❌        | ❌      |
| Usuários — gestão          | ✅    | ❌      | ❌        | ❌      |
| Config. Financeira         | ✅    | ❌      | ❌        | ❌      |
| Auditoria                  | ✅    | ✅      | ❌        | ❌      |
