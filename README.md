# Sistema de Restauração - API REST

Sistema de gestão inteligente de mesas, pedidos e pagamentos por QR Code para restaurantes com controle de concorrência, auditoria completa, autenticação JWT e geração de QR Codes dinâmicos.

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 3.2.2**
- **Spring Data JPA / Hibernate**
- **Spring Security + JWT** (autenticação e autorização RBAC)
- **PostgreSQL**
- **H2 Database** (testes)
- **Spring Validation**
- **Spring Scheduling** (jobs automáticos)
- **WebSocket** (notificações em tempo real)
- **ZXing 3.5.3** (geração de QR Codes)
- **OpenAPI/Swagger** (documentação automática da API)
- **Lombok** (redução de boilerplate)
- **MapStruct** (mapeamento de DTOs)

## 📋 Pré-requisitos

- Java 17 ou superior
- PostgreSQL 12 ou superior
- Maven 3.8 ou superior

## 🎯 Status do Projeto

**Progresso:** 40% (6 de 15 etapas concluídas)

### ✅ Etapas Concluídas:

1. **ETAPA 01** - Análise Arquitetural (40% de aderência aos princípios DDD)
2. **ETAPA 02** - Controle de Concorrência Otimista + Auditoria
   - Implementação de `@Version` em todas entidades
   - Classe base `BaseEntity` com campos de auditoria (createdAt, updatedAt, createdBy, updatedBy)
3. **ETAPA 03** - Modelo de Domínio Operacional
   - Entidades: UnidadeDeConsumo, SubPedido, ItemSubPedido
   - Suporte a múltiplos sub-pedidos por unidade de consumo
4. **ETAPA 04** - Event Log (Registro de Eventos)
   - Auditoria completa de todas operações do sistema
   - Rastreamento de mudanças de estado
5. **ETAPA 05** - Autenticação JWT + RBAC
   - Spring Security configurado
   - 4 perfis: ATENDENTE, GERENTE, ADMIN, COZINHA
   - Tokens JWT com expiração configurável
   - Endpoints protegidos por roles
6. **ETAPA 06** - QR Code Seguro com ZXing ⭐ (Recém implementado)
   - 3 tipos de QR Code: MESA (24h), ENTREGA (30min), PAGAMENTO (1h)
   - Validação automática de expiração
   - Jobs agendados: expiração (horária), renovação (diária), limpeza (mensal)
   - 8 endpoints REST para gestão completa
   - Geração de imagens PNG em múltiplos tamanhos

### 🚧 Próximas Etapas:

7. **ETAPA 07** - Sistema de Impressão e Bridge Local
8. **ETAPA 08** - Notificações em Tempo Real (WebSocket)
9. **ETAPA 09** - Painel Operacional Mobile (Garçons)
10. **ETAPA 10** - Painel Cozinha Especializado
11. **ETAPA 11** - Painel Gerencial com Intervenções
12. **ETAPA 12** - Otimização de Performance (Cache Redis)
13. **ETAPA 13** - Monitoramento (Prometheus + Grafana)
14. **ETAPA 14** - Fundo de Consumo (Saldo Pré-pago)
15. **ETAPA 15** - Testes E2E + Documentação Final

## ⚙️ Configuração

1. **Clone o repositório**

2. **Configure o banco de dados PostgreSQL**

```sql
CREATE DATABASE restaurante_db;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE restaurante_db TO postgres;
```

3. **Configure as propriedades da aplicação**

Edite o arquivo `src/main/resources/application.properties` conforme necessário:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/restaurante_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

4. **Execute a aplicação**

```bash
mvn spring-boot:run
```

A aplicação estará disponível em: `http://localhost:8080/api`

## 📚 Documentação da API

Acesse a documentação interativa Swagger UI em:
```
http://localhost:8080/api/swagger-ui.html
```

## 🏗️ Arquitetura

O projeto segue uma arquitetura em camadas bem definida:

```
src/main/java/com/restaurante/
├── config/              # Configurações (CORS, WebSocket, OpenAPI)
├── controller/          # Controllers REST
├── dto/                 # Data Transfer Objects
│   ├── request/        # DTOs de requisição
│   └── response/       # DTOs de resposta
├── exception/          # Exceções personalizadas e handlers
├── model/              # Entidades e Enums
│   ├── entity/        # Entidades JPA
│   └── enums/         # Enumerações
├── repository/         # Repositories JPA
└── service/            # Lógica de negócio
```

## 📊 Modelo de Dados

### Entidades Principais

#### Domínio Operacional (ETAPA 03)
1. **UnidadeDeConsumo** - Unidade de consumo (mesa, balcão, delivery) com QR Code exclusivo
2. **SubPedido** - Sub-pedido dentro de uma unidade de consumo (garçom específico)
3. **ItemSubPedido** - Item individual dentro de um sub-pedido

#### Domínio Base
4. **Cliente** - Cliente identificado por telefone
5. **Atendente** - Funcionário com perfil de acesso (ATENDENTE, GERENTE, ADMIN, COZINHA)
6. **Mesa** - Mesa física do restaurante
7. **Produto** - Itens do cardápio
8. **Pedido** - Pedido feito por um cliente
9. **ItemPedido** - Item individual dentro de um pedido
10. **Pagamento** - Pagamento de uma mesa

#### Auditoria e Eventos (ETAPA 04)
11. **EventLog** - Registro de todas operações do sistema (70+ tipos de eventos)

#### QR Code Seguro (ETAPA 06)
12. **QrCodeToken** - Token de QR Code com expiração e validação
   - **TipoQrCode**: MESA (24h), ENTREGA (30min), PAGAMENTO (1h)
   - **StatusQrCode**: ATIVO, USADO, EXPIRADO, CANCELADO

### Relacionamentos

- UnidadeDeConsumo → SubPedido (1:N)
- SubPedido → ItemSubPedido (1:N)
- UnidadeDeConsumo → QrCodeToken (1:N)
- Cliente → Mesa (1:N)
- Mesa → Pedido (1:N)
- Pedido → ItemPedido (1:N)
- Produto → ItemPedido (1:N)
- Mesa → Pagamento (1:1)
- Atendente → Mesa (1:N)

## 🔐 Autenticação e Segurança

### Sistema JWT + RBAC (ETAPA 05)

O sistema implementa autenticação baseada em JWT (JSON Web Tokens) com controle de acesso baseado em roles (RBAC).

#### Perfis de Acesso:
- **ATENDENTE** - Garçons e atendentes (criar pedidos, gerenciar mesas)
- **GERENTE** - Gerentes de operação (aprovações, relatórios)
- **ADMIN** - Administradores (configurações, usuários)
- **COZINHA** - Equipe de cozinha (visualizar e atualizar status de pedidos)

#### Fluxo de Autenticação:

**Para Clientes:**
1. Cliente escaneia QR Code da mesa
2. Sistema solicita telefone
3. Código OTP é enviado para o telefone
4. Cliente valida OTP
5. Unidade de consumo é criada/associada

**Para Atendentes:**
1. Login com username e senha
2. Sistema gera token JWT
3. Token incluído em todas requisições (Authorization: Bearer {token})
4. Endpoints protegidos por `@PreAuthorize` validam permissões

### QR Code Seguro (ETAPA 06)

Sistema robusto de geração e validação de QR Codes usando ZXing:

#### Tipos de QR Code:
- **MESA** - 24 horas de validade, uso múltiplo (clientes acessam cardápio)
- **ENTREGA** - 30 minutos de validade, uso único (confirmação de entrega)
- **PAGAMENTO** - 1 hora de validade, uso único (checkout)

#### Características:
- Token UUID único por QR Code
- Validação automática de expiração
- Renovação automática de QR Codes de mesa (diariamente às 6h)
- Limpeza automática de tokens expirados (mensalmente)
- Jobs agendados com Spring Scheduling
- Geração de imagens PNG em múltiplos tamanhos (150x150, 300x300, 500x500)

## 🎯 Funcionalidades Principais

### Para Clientes:
- ✅ Escanear QR Code da mesa (gerado dinamicamente)
- ✅ Autenticação via OTP (telefone)
- ✅ Visualizar cardápio
- ✅ Fazer pedidos em sub-pedidos (múltiplos garçons)
- ✅ Adicionar múltiplos sub-pedidos à mesma unidade de consumo
- ✅ Acompanhar status dos pedidos
- ✅ Visualizar conta total
- ✅ QR Code de pagamento com expiração
- ⏳ Realizar pagamento digital (estrutura preparada)

### Para Atendentes (Garçons):
- ✅ Login com JWT (username/senha)
- ✅ Visualizar todas as mesas/unidades abertas
- ✅ Criar unidade de consumo manualmente
- ✅ Criar sub-pedidos associados ao seu atendimento
- ✅ Gerar QR Code de entrega para confirmação
- ✅ Receber notificações de novos pedidos (WebSocket preparado)
- ✅ Gerenciar status dos sub-pedidos
- ✅ Controle de concorrência otimista (evita conflitos)

### Para Gerentes:
- ✅ Todas funcionalidades de atendentes
- ✅ Aprovar/recusar pagamentos
- ✅ Fechar unidades de consumo
- ✅ Gerenciar QR Codes (renovar, cancelar)
- ✅ Visualizar histórico de eventos (auditoria completa)
- ✅ Relatórios (estrutura preparada)

### Para Administração:
- ✅ Todas funcionalidades anteriores
- ✅ Gerenciar produtos do cardápio
- ✅ Controlar disponibilidade de produtos
- ✅ Gerenciar usuários e perfis de acesso
- ✅ Configurar parâmetros do sistema
- ✅ Acesso completo ao Event Log

### Para Cozinha:
- ✅ Login com JWT
- ✅ Visualizar pedidos da cozinha (RECEBIDO, EM_PREPARO)
- ✅ Atualizar status de preparação dos itens
- ✅ Marcar pedidos como prontos
- ⏳ Painel especializado (ETAPA 10)

## 📱 Endpoints Principais

### Autenticação
- `POST /api/auth/solicitar-otp` - Solicita código OTP (clientes)
- `POST /api/auth/validar-otp` - Valida OTP e autentica
- `POST /api/auth/login` - Login JWT (atendentes, gerentes, admin, cozinha)
- `POST /api/auth/refresh` - Renovar token JWT

### Mesas
- `POST /api/mesas` - Criar mesa
- `GET /api/mesas/abertas` - Listar mesas abertas
- `GET /api/mesas/qrcode/{qrCode}` - Buscar mesa por QR Code
- `PUT /api/mesas/{id}/fechar` - Fechar mesa (GERENTE, ADMIN)

### Unidades de Consumo (ETAPA 03)
- `POST /api/unidades-consumo` - Criar unidade de consumo
- `GET /api/unidades-consumo/abertas` - Listar unidades abertas
- `GET /api/unidades-consumo/{id}` - Buscar por ID
- `PUT /api/unidades-consumo/{id}/fechar` - Fechar unidade (GERENTE, ADMIN)

### Sub-Pedidos (ETAPA 03)
- `POST /api/sub-pedidos` - Criar sub-pedido
- `GET /api/sub-pedidos/unidade-consumo/{id}` - Listar por unidade
- `GET /api/sub-pedidos/ativos` - Listar ativos
- `PUT /api/sub-pedidos/{id}/status` - Atualizar status
- `DELETE /api/sub-pedidos/{id}` - Cancelar sub-pedido

### QR Code (ETAPA 06)
- `POST /api/qrcode` - Gerar QR Code (ATENDENTE, GERENTE, ADMIN)
- `GET /api/qrcode/validar/{token}` - Validar QR Code (público)
- `POST /api/qrcode/usar/{token}` - Marcar como usado
- `POST /api/qrcode/renovar/{token}` - Renovar QR Code
- `DELETE /api/qrcode/{token}` - Cancelar QR Code (GERENTE, ADMIN)
- `GET /api/qrcode/unidade-consumo/{id}` - Listar por unidade
- `GET /api/qrcode/imagem/{token}` - Obter imagem PNG (300x300)
- `GET /api/qrcode/imagem/{token}/print` - Obter imagem para impressão (500x500)

### Produtos
- `GET /api/produtos` - Listar produtos disponíveis
- `GET /api/produtos/categoria/{categoria}` - Filtrar por categoria
- `POST /api/produtos` - Criar produto
- `PUT /api/produtos/{id}` - Atualizar produto

### Pedidos
- `POST /api/pedidos` - Criar pedido
- `GET /api/pedidos/mesa/{mesaId}` - Listar pedidos da mesa
- `GET /api/pedidos/ativos` - Listar pedidos ativos
- `PUT /api/pedidos/{id}/avancar` - Avançar status do pedido

### Pagamentos
- `POST /api/pagamentos` - Criar pagamento
- `PUT /api/pagamentos/{id}/aprovar` - Aprovar pagamento
- `GET /api/pagamentos/mesa/{mesaId}` - Buscar pagamento da mesa

## 🔄 Estados (Status)

### StatusUnidadeDeConsumo (ETAPA 03)
- `DISPONIVEL` - Unidade livre
- `OCUPADA` - Unidade em uso
- `AGUARDANDO_PAGAMENTO` - Sub-pedidos finalizados, aguardando pagamento
- `FINALIZADA` - Unidade fechada

### StatusSubPedido (ETAPA 03)
- `PENDENTE` - Sub-pedido criado
- `RECEBIDO` - Confirmado pela cozinha
- `EM_PREPARO` - Sendo preparado
- `PRONTO` - Pronto para servir
- `ENTREGUE` - Entregue ao cliente
- `CANCELADO` - Cancelado

### StatusMesa
- `DISPONIVEL` - Mesa livre
- `OCUPADA` - Mesa em uso
- `AGUARDANDO_PAGAMENTO` - Pedidos finalizados, aguardando pagamento
- `FINALIZADA` - Mesa fechada

### StatusPedido
- `PENDENTE` - Pedido criado
- `RECEBIDO` - Confirmado pela cozinha
- `EM_PREPARO` - Sendo preparado
- `PRONTO` - Pronto para servir
- `ENTREGUE` - Entregue ao cliente
- `CANCELADO` - Cancelado

### StatusPagamento
- `PENDENTE` - Aguardando processamento
- `PROCESSANDO` - Em processamento
- `APROVADO` - Pagamento aprovado
- `RECUSADO` - Pagamento recusado
- `CANCELADO` - Pagamento cancelado

### StatusQrCode (ETAPA 06)
- `ATIVO` - QR Code válido e utilizável
- `USADO` - QR Code já utilizado (uso único)
- `EXPIRADO` - QR Code expirado (tempo limite atingido)
- `CANCELADO` - QR Code cancelado manualmente

### TipoQrCode (ETAPA 06)
- `MESA` - 24 horas de validade, uso múltiplo
- `ENTREGA` - 30 minutos de validade, uso único
- `PAGAMENTO` - 1 hora de validade, uso único

## 🚧 Próximos Passos

### Em Desenvolvimento
- [ ] **ETAPA 07** - Sistema de Impressão e Bridge Local
  - Eventos de impressão via WebSocket
  - Bridge para comunicação com impressoras térmicas locais
  - Templates de impressão (comanda, ticket cozinha, conta)

### Planejado
- [ ] **ETAPA 08** - Notificações em Tempo Real (WebSocket completo)
  - Canais especializados por perfil (/topic/cozinha, /topic/garcom, /topic/cliente)
  - Heartbeat para manter conexões ativas
  - Notificações de mudança de status de pedidos
  
- [ ] **ETAPA 09** - Painel Operacional Mobile (Garçons)
  - Interface otimizada para tablets/smartphones
  - Gestão de sub-pedidos em tempo real
  - Confirmação de entregas via QR Code
  
- [ ] **ETAPA 10** - Painel Cozinha Especializado
  - Visualização otimizada para cozinha
  - Priorização de pedidos
  - Timeline de preparação
  
- [ ] **ETAPA 11** - Painel Gerencial com Intervenções
  - Dashboard executivo
  - Intervenções em pedidos e mesas
  - Relatórios gerenciais
  
- [ ] **ETAPA 12** - Otimização de Performance
  - Cache Redis (produtos, configurações)
  - Otimização de queries JPA
  - Connection pooling
  
- [ ] **ETAPA 13** - Monitoramento (Prometheus + Grafana)
  - Métricas de performance
  - Alertas configuráveis
  - Dashboards personalizados
  
- [ ] **ETAPA 14** - Fundo de Consumo (Saldo Pré-pago)
  - Carteira digital por cliente
  - Recarga de saldo
  - Histórico de transações
  
- [ ] **ETAPA 15** - Testes E2E + Documentação Final
  - Testes de integração completos
  - Documentação técnica detalhada
  - Guias de deploy e operação

## 📝 Observações Importantes

### Regras de Negócio

1. **Controle de Concorrência (ETAPA 02)**: Todas entidades possuem controle otimista com `@Version`, evitando conflitos em atualizações simultâneas.

2. **Auditoria Completa (ETAPA 02 + 04)**: 
   - Todas entidades herdam de `BaseEntity` com timestamps e usuário responsável
   - `EventLog` registra todas operações do sistema (70+ tipos de eventos)
   - Rastreabilidade completa de mudanças

3. **Unidade de Consumo (ETAPA 03)**: Abstração que unifica mesa, balcão e delivery. Permite múltiplos sub-pedidos atendidos por garçons diferentes.

4. **Sub-Pedidos (ETAPA 03)**: Cada sub-pedido é atendido por um garçom específico, permitindo rastreamento individual e divisão de responsabilidades.

5. **Segurança RBAC (ETAPA 05)**: 
   - 4 perfis com permissões específicas
   - Endpoints protegidos com `@PreAuthorize`
   - Tokens JWT com expiração configurável

6. **QR Codes Inteligentes (ETAPA 06)**:
   - Validade automática conforme tipo
   - Renovação automática de QR Codes de mesa (diariamente)
   - Limpeza automática de tokens expirados (mensalmente)
   - Jobs agendados com Spring Scheduling

7. **Mesa sempre associada a cliente**: Uma mesa só existe quando está associada a um número de telefone validado.

8. **Fechamento controlado**: Unidades de consumo só podem ser fechadas após pagamento aprovado.

9. **Cancelamento de pedidos**: Sub-pedidos só podem ser cancelados nos estados PENDENTE ou RECEBIDO.

### Arquitetura e Boas Práticas

- **Separação de Camadas**: Controller → Service → Repository
- **DTOs para API**: Request/Response objects separados das entidades
- **Exception Handling**: GlobalExceptionHandler centralizado
- **Validação**: Bean Validation (JSR-380) em DTOs
- **Documentação**: OpenAPI 3.0 com Swagger UI
- **Logging**: SLF4J + Logback
- **Mapeamento**: MapStruct para conversões DTO ↔ Entity

## 🛠️ Jobs Agendados

O sistema possui 3 jobs automáticos (Spring Scheduling):

1. **Expiração de QR Codes** - Executa a cada hora
   - Marca QR Codes expirados automaticamente
   - Cron: `0 0 * * * *`

2. **Renovação de QR Codes de Mesa** - Executa diariamente às 6h
   - Renova QR Codes de mesa ativos automaticamente
   - Cron: `0 0 6 * * *`

3. **Limpeza de Tokens Antigos** - Executa mensalmente (dia 3, 3h da manhã)
   - Remove QR Codes expirados há mais de 30 dias
   - Cron: `0 0 3 1 * *`

## 🧪 Testes Automatizados

O sistema possui **32 testes automatizados** que validam os módulos críticos:

- ✅ **ClienteServiceTest**: 10 testes (autenticação OTP)
- ✅ **NotificacaoServiceTest**: 11 testes (notificações SMS)
- ✅ **Outros módulos**: 11 testes (produtos, fundos, pedidos, etc.)

**Cobertura:** ~80% nos módulos de autenticação e notificações.

📖 **Documentação completa:** [TESTES_AUTOMATIZADOS.md](TESTES_AUTOMATIZADOS.md)

### Executar os testes
```bash
# Todos os testes
mvn test

# Testes específicos
mvn test -Dtest=ClienteServiceTest,NotificacaoServiceTest

# Com relatório de cobertura
mvn clean test jacoco:report
```

## 📊 Métricas do Projeto

- **Arquivos Java**: 153 arquivos compilados
- **Linhas de Código**: ~18.000 linhas
- **Entidades JPA**: 12 entidades principais
- **Endpoints REST**: 50+ endpoints
- **Tipos de Eventos**: 70+ tipos no EventLog
- **Perfis de Acesso**: 4 roles (ATENDENTE, GERENTE, ADMIN, COZINHA)
- **Tipos de QR Code**: 3 tipos com regras específicas
- **Testes Automatizados**: 32 testes (100% passando)

## 🤝 Contribuindo

Este projeto segue um plano de desenvolvimento estruturado em 15 etapas. Atualmente na ETAPA 06 (40% concluído).

### Como Contribuir:
1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/MinhaFeature`)
3. Commit suas mudanças (`git commit -m 'Adiciona MinhaFeature'`)
4. Push para a branch (`git push origin feature/MinhaFeature`)
5. Abra um Pull Request

### Padrões de Código:
- Java 17+ com recursos modernos
- Spring Boot best practices
- Clean Code e SOLID principles
- Testes unitários para novas funcionalidades
- Documentação de endpoints com OpenAPI

## 📄 Licença

Projeto desenvolvido para fins educacionais e profissionais.

## 🔗 Links Úteis

- **Repositório GitHub**: [https://github.com/Eds0nTom4s/maspe-residencial.git](https://github.com/Eds0nTom4s/maspe-residencial.git)
- **Documentação API (Swagger)**: `http://localhost:8080/api/swagger-ui.html`
- **H2 Console (dev)**: `http://localhost:8080/api/h2-console`

## 👥 Equipe

Desenvolvido por Eng. Margarida e equipe.

---

**Desenvolvido com ❤️ usando Spring Boot 3.2.2 + Java 17**

*Última atualização: ETAPA 06 - QR Code Seguro com ZXing (10/02/2026)*
