# Sistema de Restauração - API REST

Sistema de gestão inteligente de mesas, pedidos e pagamentos por QR Code para restaurantes.

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 3.2.2**
- **Spring Data JPA / Hibernate**
- **PostgreSQL**
- **Spring Validation**
- **WebSocket** (preparado para notificações em tempo real)
- **OpenAPI/Swagger** (documentação automática da API)
- **Lombok** (redução de boilerplate)
- **MapStruct** (mapeamento de DTOs)

## 📋 Pré-requisitos

- Java 17 ou superior
- PostgreSQL 12 ou superior
- Maven 3.8 ou superior

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

1. **Cliente** - Representa um cliente identificado por telefone
2. **Atendente** - Funcionário que opera o painel administrativo
3. **Mesa** - Mesa física do restaurante (associada obrigatoriamente a um cliente)
4. **Produto** - Itens do cardápio
5. **Pedido** - Pedido feito por um cliente em uma mesa
6. **ItemPedido** - Item individual dentro de um pedido
7. **Pagamento** - Pagamento de uma mesa (estrutura preparada para gateway)

### Relacionamentos

- Cliente → Mesa (1:N)
- Mesa → Pedido (1:N)
- Pedido → ItemPedido (1:N)
- Produto → ItemPedido (1:N)
- Mesa → Pagamento (1:1)
- Atendente → Mesa (1:N) - quando criada manualmente

## 🔐 Autenticação

O sistema utiliza autenticação simples via OTP (One-Time Password) enviado por SMS/WhatsApp.

### Fluxo de autenticação:

1. Cliente escaneia QR Code da mesa
2. Sistema solicita telefone
3. Código OTP é enviado para o telefone
4. Cliente valida OTP
5. Mesa é criada e associada ao cliente

## 🎯 Funcionalidades Principais

### Para Clientes:
- ✅ Escanear QR Code da mesa
- ✅ Autenticação via OTP (telefone)
- ✅ Visualizar cardápio
- ✅ Fazer pedidos
- ✅ Adicionar múltiplos pedidos à mesma mesa
- ✅ Acompanhar status dos pedidos
- ✅ Visualizar conta total
- ⏳ Realizar pagamento digital (estrutura preparada)

### Para Atendentes:
- ✅ Visualizar todas as mesas abertas
- ✅ Criar mesa manualmente (para clientes sem telefone)
- ✅ Receber notificações de novos pedidos (WebSocket preparado)
- ✅ Gerenciar status dos pedidos
- ✅ Aprovar/recusar pagamentos
- ✅ Fechar mesas

### Para Administração:
- ✅ Gerenciar produtos do cardápio
- ✅ Controlar disponibilidade de produtos
- ✅ Visualizar histórico de pedidos
- ✅ Relatórios (estrutura preparada)

## 📱 Endpoints Principais

### Autenticação
- `POST /api/auth/solicitar-otp` - Solicita código OTP
- `POST /api/auth/validar-otp` - Valida OTP e autentica

### Mesas
- `POST /api/mesas` - Criar mesa
- `GET /api/mesas/abertas` - Listar mesas abertas
- `GET /api/mesas/qrcode/{qrCode}` - Buscar mesa por QR Code
- `PUT /api/mesas/{id}/fechar` - Fechar mesa

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

## 🚧 Próximos Passos (TODOs)

- [ ] Implementar serviço de envio de SMS/WhatsApp para OTP
- [ ] Integrar com gateway de pagamento (PIX, cartão)
- [ ] Implementar notificações em tempo real via WebSocket
- [ ] Adicionar autenticação JWT para atendentes
- [ ] Implementar sistema de relatórios
- [ ] Adicionar testes unitários e de integração
- [ ] Implementar cache (Redis)
- [ ] Adicionar logs estruturados
- [ ] Implementar sistema de filas (RabbitMQ/Kafka)

## 📝 Observações Importantes

### Regras de Negócio

1. **Mesa sempre associada a cliente**: Uma mesa só existe quando está associada a um número de telefone validado, evitando mesas fantasmas.

2. **Cliente único por mesa**: Um cliente pode ter apenas uma mesa ativa por vez.

3. **Pedidos múltiplos**: Um cliente pode fazer vários pedidos para a mesma mesa.

4. **Fechamento de mesa**: Mesa só pode ser fechada após pagamento aprovado.

5. **Cancelamento de pedidos**: Pedidos só podem ser cancelados nos estados PENDENTE ou RECEBIDO.

## 🤝 Contribuindo

Este é um projeto base. Sinta-se à vontade para expandir e adaptar conforme suas necessidades.

## 📄 Licença

Projeto desenvolvido para fins educacionais e profissionais.

---

**Desenvolvido com ❤️ usando Spring Boot**
