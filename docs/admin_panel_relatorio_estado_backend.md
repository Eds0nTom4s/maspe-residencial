# Relatório Técnico — Estado do Backend para o Painel Administrativo

**Data:** 2026-03-16  
**Versão do Sistema:** Sistema de Restauração MASPE  
**Status do Backend:** Em execução (perfil dev)

---

## 1. Estado Atual do Backend

### 1.1 Resumo Geral

O backend está funcional e em execução. A base do sistema está bem modelada com 
separação clara de responsabilidades entre entidades de domínio, services e controllers.

| Componente           | Quantidade | Estado               |
|----------------------|------------|----------------------|
| Controllers          | 23         | ✅ Funcionais         |
| Services             | 18         | ✅ Funcionais         |
| Entidades de Domínio | 23         | ✅ Funcionais         |
| Enums de Estado      | 19         | ✅ Bem definidos      |
| Roles (perfis)       | 5          | ✅ Implementados com JWT |

### 1.2 Roles do Sistema

| Role           | Descrição                                |
|----------------|------------------------------------------|
| ROLE_ADMIN     | Acesso total — configurações e operações |
| ROLE_GERENTE   | Gestão operacional e financeira          |
| ROLE_ATENDENTE | Operação de mesa e pedidos               |
| ROLE_COZINHA   | Execução de SubPedidos                   |
| ROLE_CLIENTE   | Fluxo QR Ordering (app cliente)          |

### 1.3 Autenticação

- **Clientes:** OTP via SMS (TelcoSMS) → JWT com role CLIENTE
- **Operadores (Admin/Gerente/Atendente/Cozinha):** Telefone + Senha → JWT com role correspondente
- **Endpoint de login de operadores:** `POST /api/auth/admin/login`

---

## 2. Endpoints Existentes e Reutilizáveis pelo Painel

### 2.1 Autenticação

| Método | Endpoint                  | Roles | Descrição                             |
|--------|---------------------------|-------|---------------------------------------|
| POST   | `/auth/solicitar-otp`     | ANY   | OTP para clientes                     |
| POST   | `/auth/validar-otp`       | ANY   | Validar OTP → JWT CLIENTE             |
| POST   | `/auth/admin/login`       | ANY   | Login operadores (telefone + senha)   |

### 2.2 Dashboard

| Método | Endpoint                    | Roles               | Descrição                     |
|--------|-----------------------------|---------------------|-------------------------------|
| GET    | `/dashboard/stats`          | ADMIN, GERENTE, ATENDENTE | Estatísticas gerais ⚠️ MOCK |
| GET    | `/dashboard/activity`       | ADMIN, GERENTE, ATENDENTE | Atividades recentes ⚠️ MOCK  |
| GET    | `/dashboard/top-products`   | ADMIN, GERENTE      | Produtos mais vendidos ⚠️ MOCK|

> ⚠️ **CRÍTICO:** O `DashboardService` retorna dados **mockados hardcoded**. Não reflete dados reais do banco.

### 2.3 Sessões de Consumo

| Método | Endpoint                              | Roles                          | Descrição                          |
|--------|---------------------------------------|--------------------------------|------------------------------------|
| POST   | `/sessoes-consumo`                    | ATENDENTE, GERENTE, ADMIN      | Abrir sessão                       |
| PUT    | `/sessoes-consumo/{id}/fechar`        | ATENDENTE, GERENTE, ADMIN      | Encerrar sessão                    |
| PUT    | `/sessoes-consumo/{id}/aguardar-pagamento` | ATENDENTE, GERENTE, ADMIN | Sinalizar aguardando pagamento     |
| GET    | `/sessoes-consumo/{id}`               | Autenticado                    | Buscar por ID                      |
| GET    | `/sessoes-consumo/abertas`            | ATENDENTE, GERENTE, ADMIN      | Listar sessões abertas ✅           |
| GET    | `/sessoes-consumo/mesa/{id}/ativa`    | Autenticado                    | Sessão ativa de uma mesa           |
| GET    | `/sessoes-consumo/mesa/{id}/historico`| Autenticado                    | Histórico de sessões por mesa      |

### 2.4 Mesas

| Método | Endpoint                                      | Roles            | Descrição                            |
|--------|-----------------------------------------------|------------------|--------------------------------------|
| POST   | `/mesas`                                      | ADMIN            | Criar mesa                           |
| PUT    | `/mesas/{id}/ativar`                          | ADMIN            | Ativar mesa                          |
| PUT    | `/mesas/{id}/desativar`                       | ADMIN            | Desativar mesa                       |
| GET    | `/mesas`                                      | Autenticado      | Listar todas com status derivado     |
| GET    | `/mesas/disponiveis`                          | Autenticado      | Listar DISPONÍVEIS                   |
| GET    | `/mesas/ocupadas`                             | Autenticado      | Listar OCUPADAS                      |
| GET    | `/mesas/{id}`                                 | Autenticado      | Buscar por ID                        |
| GET    | `/mesas/ativas`                               | Autenticado      | Listar ativas                        |
| GET    | `/mesas/qrcode/{qrCode}`                      | Autenticado      | Buscar por QR Code                   |
| GET    | `/mesas/unidade-atendimento/{id}`             | Autenticado      | Mesas por Unidade                    |

> ⚠️ **GAP:** Sem endpoint para **editar** dados de uma mesa (referência, capacidade, etc.)

### 2.5 Pedidos

| Método | Endpoint                                       | Roles                     | Descrição                           |
|--------|------------------------------------------------|---------------------------|-------------------------------------|
| POST   | `/pedidos`                                     | ATENDENTE, GERENTE, ADMIN | Criar pedido (atendente)            |
| POST   | `/pedidos/cliente`                             | CLIENTE                   | Criar pedido (QR)                   |
| GET    | `/pedidos/{id}`                                | Autenticado               | Buscar por ID                       |
| GET    | `/pedidos/numero/{numero}`                     | Autenticado               | Buscar por número                   |
| GET    | `/pedidos/status/{status}`                     | Autenticado               | Listar por status                   |
| GET    | `/pedidos/ativos`                              | Autenticado               | Listar pedidos ativos               |
| GET    | `/pedidos/sessao-consumo/{id}`                 | ATENDENTE, GERENTE, ADMIN | Pedidos de uma sessão               |
| GET    | `/pedidos/sessao-consumo/{id}/ativo`           | ATENDENTE, GERENTE, ADMIN | Pedidos ativos de uma sessão        |
| PUT    | `/pedidos/{id}/confirmar`                      | ATENDENTE, GERENTE, ADMIN | Confirmar (CRIADO→EM_ANDAMENTO)     |
| PUT    | `/pedidos/{id}/cancelar`                       | GERENTE, ADMIN            | Cancelar com motivo                 |
| PUT    | `/pedidos/{id}/confirmar-pagamento`            | GERENTE, ADMIN            | Confirmar pagamento pós-pago        |
| PUT    | `/pedidos/{id}/fechar`                         | ATENDENTE, GERENTE, ADMIN | Fechar conta e liberar mesa         |

> ⚠️ **GAP:** Sem endpoint para listar **todos os pedidos do dia** com filtros de data.

### 2.6 Sub-Pedidos (Cozinha)

| Método | Endpoint                               | Roles                     | Descrição                              |
|--------|----------------------------------------|---------------------------|----------------------------------------|
| GET    | `/subpedidos/{id}`                     | Autenticado               | Buscar por ID                          |
| GET    | `/subpedidos/pedido/{id}`              | Autenticado               | Sub-pedidos de um pedido               |
| GET    | `/subpedidos/cozinha/{id}/ativos`      | Autenticado               | Ativos por cozinha ✅                  |
| GET    | `/subpedidos/cozinha/{id}/prontos`     | Autenticado               | Prontos por cozinha ✅                 |
| GET    | `/subpedidos/atrasados`                | Autenticado               | Com atraso (filtro por minutos)        |
| GET    | `/subpedidos/kpi/tempo-medio`          | Autenticado               | KPI tempo médio por cozinha            |
| PUT    | `/subpedidos/{id}/assumir`             | COZINHA, GERENTE, ADMIN   | PENDENTE→EM_PREPARACAO                 |
| PUT    | `/subpedidos/{id}/marcar-pronto`       | COZINHA, GERENTE, ADMIN   | EM_PREPARACAO→PRONTO                   |
| PUT    | `/subpedidos/{id}/marcar-entregue`     | ATENDENTE, GERENTE, ADMIN | PRONTO→ENTREGUE                        |
| PUT    | `/subpedidos/{id}/cancelar`            | GERENTE, ADMIN            | Cancelar com motivo                    |

### 2.7 Produtos

| Método | Endpoint                              | Roles             | Descrição                          |
|--------|---------------------------------------|-------------------|------------------------------------|
| POST   | `/produtos`                           | GERENTE, ADMIN    | Criar produto                      |
| PUT    | `/produtos/{id}`                      | GERENTE, ADMIN    | Atualizar produto                  |
| PATCH  | `/produtos/{id}/disponibilidade`      | GERENTE, ADMIN    | Ligar/desligar disponibilidade     |
| DELETE | `/produtos/{id}`                      | GERENTE, ADMIN    | Soft delete                        |
| GET    | `/produtos`                           | Autenticado       | Listar disponíveis                 |
| GET    | `/produtos/categoria/{categoria}`     | Autenticado       | Por categoria                      |
| GET    | `/produtos/buscar?nome=`              | Autenticado       | Buscar por nome                    |

> ⚠️ **GAP:** Sem endpoint para listar **todos os produtos** (incluindo indisponíveis) para gestão admin. Sem `GET /produtos/{id}`.

### 2.8 Cozinhas

| Método | Endpoint                                            | Roles       | Descrição                        |
|--------|-----------------------------------------------------|-------------|----------------------------------|
| POST   | `/cozinhas`                                         | Autenticado | Criar cozinha                    |
| GET    | `/cozinhas`                                         | Autenticado | Listar todas                     |
| GET    | `/cozinhas/{id}`                                    | Autenticado | Buscar por ID                    |
| GET    | `/cozinhas/ativas`                                  | Autenticado | Listar ativas                    |
| GET    | `/cozinhas/tipo/{tipo}`                             | Autenticado | Filtrar por tipo                 |
| PUT    | `/cozinhas/{id}/ativar`                             | Autenticado | Ativar                           |
| PUT    | `/cozinhas/{id}/desativar`                          | Autenticado | Desativar                        |
| PUT    | `/cozinhas/{id}/impressora`                         | Autenticado | Atualizar ID impressora          |
| POST   | `/cozinhas/{id}/vincular/{unidadeId}`               | Autenticado | Vincular a unidade               |
| DELETE | `/cozinhas/{id}/desvincular/{unidadeId}`            | Autenticado | Desvincular de unidade           |

> ⚠️ **GAP:** Endpoints de cozinha **não possuem `@PreAuthorize`** — sem proteção de roles.

### 2.9 Fundo de Consumo

| Método | Endpoint                          | Roles             | Descrição                          |
|--------|-----------------------------------|-------------------|------------------------------------|
| GET    | `/fundos/{token}`                 | ATENDENTE, GERENTE, ADMIN | Consultar fundo          |
| GET    | `/fundos/{token}/saldo`           | ATENDENTE, GERENTE, ADMIN | Consultar saldo          |
| GET    | `/fundos/{token}/historico`       | GERENTE, ADMIN    | Histórico de transações            |
| GET    | `/fundos/sessao/{sessaoId}`       | GERENTE, ADMIN    | Buscar fundo por sessão            |
| POST   | `/fundos/{token}/recarregar`      | GERENTE, ADMIN    | Recarregar fundo                   |
| DELETE | `/fundos/{token}`                 | ADMIN             | Encerrar fundo                     |

> ⚠️ **GAP:** Sem endpoint `GET /fundos` para **listar todos os fundos ativos** com saldo e sessão.

### 2.10 Pagamentos (Gateway AppyPay)

| Método | Endpoint                      | Roles             | Descrição                       |
|--------|-------------------------------|-------------------|---------------------------------|
| POST   | `/pagamentos/recarregar`      | GERENTE, ADMIN    | Iniciar recarga via AppyPay     |
| GET    | `/pagamentos/{id}`            | ATENDENTE, GERENTE, ADMIN | Consultar pagamento    |
| GET    | `/pagamentos/fundo/{fundoId}` | GERENTE, ADMIN    | Histórico por fundo             |

> ⚠️ **GAP:** Sem endpoint para listar **todos os pagamentos** com filtros de data/status para relatórios.

### 2.11 Usuários (Operadores)

| Método | Endpoint                          | Roles  | Descrição                        |
|--------|-----------------------------------|--------|----------------------------------|
| POST   | `/usuarios`                       | ADMIN  | Criar utilizador                 |
| GET    | `/usuarios`                       | ADMIN  | Listar (paginado)                |
| GET    | `/usuarios/{id}`                  | ADMIN  | Buscar por ID                    |
| GET    | `/usuarios/permissoes`            | ADMIN  | Listar roles disponíveis         |
| GET    | `/usuarios/{id}/logs`             | ADMIN  | Logs de ações (em implementação) |
| PUT    | `/usuarios/{id}`                  | ADMIN  | Atualizar dados                  |
| PATCH  | `/usuarios/{id}/ativar`           | ADMIN  | Ativar utilizador                |
| PATCH  | `/usuarios/{id}/desativar`        | ADMIN  | Desativar utilizador             |
| DELETE | `/usuarios/{id}`                  | ADMIN  | Remover (soft delete)            |
| PATCH  | `/usuarios/{id}/senha`            | ADMIN  | Alterar senha                    |

### 2.12 Configurações Financeiras

| Método | Endpoint                                    | Roles  | Descrição                         |
|--------|---------------------------------------------|--------|-----------------------------------|
| GET    | `/configuracoes-financeiras`                | ADMIN  | Buscar configuração atual         |
| GET    | `/configuracoes-financeiras/pos-pago/status`| ATENDENTE, GERENTE, ADMIN | Status pós-pago |
| PUT    | `/configuracoes-financeiras/pos-pago/ativar`| ADMIN  | Ativar pós-pago globalmente       |
| PUT    | `/configuracoes-financeiras/pos-pago/desativar` | ADMIN | Desativar pós-pago             |
| PUT    | `/configuracoes-financeiras/pos-pago/limite`| ADMIN  | Alterar limite de pós-pago        |
| PUT    | `/configuracoes-financeiras/valor-minimo`   | ADMIN  | Alterar valor mínimo              |

### 2.13 Auditoria

| Método | Endpoint                         | Roles            | Descrição                          |
|--------|----------------------------------|------------------|------------------------------------|
| GET    | `/auditoria/acoes`               | GERENTE, ADMIN   | Listar ações com filtros           |
| GET    | `/auditoria/estatisticas`        | GERENTE, ADMIN   | Contadores de auditoria            |
| GET    | `/auditoria/modulos`             | GERENTE, ADMIN   | Tipos de evento disponíveis        |

---

## 3. Gaps Identificados — Endpoints Faltantes

### 3.1 Dashboard (CRÍTICO)

| # | Endpoint Necessário                  | Prioridade | Descrição                                    |
|---|--------------------------------------|------------|----------------------------------------------|
| 1 | `GET /dashboard/stats`               | 🔴 Alta    | Reimplementar com dados reais (atualmente mockado) |
| 2 | `GET /dashboard/activity`            | 🔴 Alta    | Atividades reais do banco                    |
| 3 | `GET /dashboard/top-products`        | 🟡 Média   | Produtos mais vendidos com dados reais       |
| 4 | `GET /dashboard/resumo-sessoes`      | 🔴 Alta    | Resumo de sessões abertas/aguardando pagamento |
| 5 | `GET /dashboard/receita?periodo=`    | 🟡 Média   | Receita do dia / semana / mês                |

### 3.2 Sessões de Consumo

| # | Endpoint Necessário                              | Prioridade | Descrição                                       |
|---|--------------------------------------------------|------------|-------------------------------------------------|
| 6 | `GET /sessoes-consumo`                           | 🔴 Alta    | Listar TODAS as sessões com filtros (status, data) |
| 7 | `GET /sessoes-consumo/aguardando-pagamento`      | 🔴 Alta    | Listar sessões AGUARDANDO_PAGAMENTO             |
| 8 | `GET /sessoes-consumo/{id}/resumo-financeiro`    | 🟡 Média   | Total consumido, saldo, pedidos por sessão      |

### 3.3 Pedidos

| # | Endpoint Necessário                         | Prioridade | Descrição                                   |
|---|---------------------------------------------|------------|---------------------------------------------|
| 9 | `GET /pedidos?data=&status=&sessaoId=`      | 🔴 Alta    | Listar todos com filtros de data e status   |
| 10| `GET /pedidos/hoje`                         | 🟡 Média   | Pedidos do dia atual                        |

### 3.4 Produtos

| # | Endpoint Necessário                        | Prioridade | Descrição                                   |
|---|--------------------------------------------|------------|---------------------------------------------|
| 11| `GET /produtos/admin` (todos, incluindo inativos) | 🔴 Alta | Gestão completa do cardápio              |
| 12| `GET /produtos/{id}`                       | 🟡 Média   | Buscar produto individual (para edição)     |

### 3.5 Relatórios Financeiros

| # | Endpoint Necessário                                  | Prioridade | Descrição                                |
|---|------------------------------------------------------|------------|------------------------------------------|
| 13| `GET /relatorios/vendas?inicio=&fim=`                | 🟡 Média   | Relatório de vendas por período          |
| 14| `GET /relatorios/sessoes?inicio=&fim=`               | 🟡 Média   | Relatório de sessões por período         |
| 15| `GET /relatorios/cozinha?cozinhaId=&data=`           | 🟢 Baixa   | KPI de produção por cozinha              |
| 16| `GET /relatorios/produtos/vendas?inicio=&fim=`       | 🟢 Baixa   | Ranking de produtos vendidos por período |

### 3.6 Segurança / Autorização

| # | Problema                                                     | Prioridade |
|---|--------------------------------------------------------------|------------|
| 17| `CozinhaController` sem `@PreAuthorize` em todos endpoints   | 🔴 Alta    |
| 18| `UnidadeAtendimentoController` sem `@PreAuthorize`           | 🔴 Alta    |
| 19| `SubPedidoController` — endpoints de leitura sem proteção de role | 🟡 Média |
| 20| `POST /usuarios/reset-senha` retorna sucesso falso sem executar nada | 🔴 Alta |

---

## 4. Inconsistências de Modelagem

| # | Inconsistência                                                             | Impacto  |
|---|----------------------------------------------------------------------------|----------|
| 1 | `DashboardService` com dados **mockados** — nunca conectado ao banco       | 🔴 Crítico |
| 2 | Sem entidade `CategoriaProduto` separada — é apenas um `enum`. Limita a gestão dinâmica de categorias | 🟡 Médio |
| 3 | `Pagamento` mistura pagamentos de **recarga de fundo** e **pedido** no mesmo model — sem separação clara para relatórios | 🟡 Médio |
| 4 | `EventoSessao` existe como entidade, mas não parece ter controller próprio — auditoria de sessão inacessível pelo painel | 🟡 Médio |
| 5 | `UserController` — endpoint `reset-senha` retorna `200 OK` sem realizar nenhuma ação real (deceptivo) | 🔴 Alto |

---

## 5. Riscos Identificados

| Risco | Severidade | Descrição |
|-------|------------|-----------|
| Dashboard com dados fictícios | 🔴 Crítico | O painel administrativo mostrará números falsos ao usuário final |
| CozinhaController sem autorização | 🔴 Crítico | Qualquer usuário autenticado pode criar/desativar cozinhas |
| Reset de senha ineficaz | 🔴 Alto | Endpoint existente não funciona, criando falsa sensação de segurança |
| Falta de paginação em listas operacionais | 🟡 Médio | Em ambientes com muitos pedidos, listas não-paginadas causarão lentidão |
| Auditoria de eventos de sessão inacessível | 🟡 Médio | `EventoSessao` não é exposto via API |
| Ausência de relatórios por período | 🟡 Médio | Impossível gerar relatórios financeiros históricos do painel |

---

## 6. Pontos Fortes do Backend

- ✅ Máquina de estados robusta (SubPedido, Pedido, SessaoConsumo)
- ✅ Separação clara entre fluxo CLIENTE (QR) e fluxo OPERADOR
- ✅ Auditoria financeira implementada (`AuditoriaFinanceiraService`)
- ✅ Integração AppyPay implementada com callback
- ✅ JWT com roles bem definidos
- ✅ Configuração financeira com log de mudanças
- ✅ Status de mesa derivado (não persistido) — correto por design
- ✅ SubPedidos com timestamps de ciclo de vida completo (recebido, iniciado, pronto, entregue)
