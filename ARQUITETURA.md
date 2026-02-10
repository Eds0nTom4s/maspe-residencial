# Arquitetura e Fluxos do Sistema

## 📐 Arquitetura em Camadas

```
┌─────────────────────────────────────────────────────────┐
│                    CAMADA DE APRESENTAÇÃO                │
│                      (Controllers)                       │
│  - AuthController                                        │
│  - MesaController                                        │
│  - PedidoController                                      │
│  - ProdutoController                                     │
│  - PagamentoController                                   │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                   CAMADA DE NEGÓCIO                      │
│                      (Services)                          │
│  - ClienteService (Autenticação OTP)                    │
│  - MesaService (Gestão de mesas)                        │
│  - PedidoService (Gestão de pedidos)                    │
│  - ProdutoService (Gestão de cardápio)                  │
│  - PagamentoService (Processamento de pagamentos)       │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                   CAMADA DE PERSISTÊNCIA                 │
│                      (Repositories)                      │
│  - ClienteRepository                                     │
│  - MesaRepository                                        │
│  - PedidoRepository                                      │
│  - ProdutoRepository                                     │
│  - PagamentoRepository                                   │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                    BANCO DE DADOS                        │
│                      PostgreSQL                          │
└─────────────────────────────────────────────────────────┘
```

## 🔄 Fluxo Principal - Cliente Escaneia QR Code

```
1. Cliente escaneia QR Code da mesa
   ↓
2. Frontend solicita OTP
   POST /api/auth/solicitar-otp
   { "telefone": "+5511999998888" }
   ↓
3. Sistema gera OTP e envia SMS/WhatsApp
   (Cliente recebe código no telefone)
   ↓
4. Cliente insere OTP no app
   POST /api/auth/validar-otp
   { "telefone": "+5511999998888", "codigo": "123456" }
   ↓
5. Sistema valida OTP e retorna dados do cliente
   ↓
6. Frontend cria/acessa mesa
   POST /api/mesas
   {
     "numero": 15,
     "telefoneCliente": "+5511999998888",
     "qrCode": "MESA-15-ABC123"
   }
   ↓
7. Cliente visualiza cardápio
   GET /api/produtos
   ↓
8. Cliente faz pedido
   POST /api/pedidos
   {
     "mesaId": 1,
     "itens": [
       { "produtoId": 5, "quantidade": 2 },
       { "produtoId": 12, "quantidade": 1 }
     ]
   }
   ↓
9. Sistema notifica atendentes via WebSocket
   → Pedido aparece no painel administrativo
   ↓
10. Cliente acompanha status do pedido em tempo real
    ↓
11. Quando todos pedidos estão entregues, cliente solicita conta
    ↓
12. Cliente realiza pagamento
    POST /api/pagamentos
    {
      "mesaId": 1,
      "valor": 125.70,
      "metodoPagamento": "PIX"
    }
    ↓
13. Pagamento aprovado
    ↓
14. Mesa é fechada
    PUT /api/mesas/1/fechar
```

## 🔐 Fluxo de Autenticação via OTP

```
┌─────────┐                                    ┌──────────────┐
│ Cliente │                                    │   Sistema    │
└────┬────┘                                    └──────┬───────┘
     │                                                │
     │  1. Solicita OTP (telefone)                   │
     │ ───────────────────────────────────────────> │
     │                                                │
     │              2. Gera OTP (6 dígitos)          │
     │                    Salva no BD                │
     │                    Envia SMS/WhatsApp         │
     │ <─────────────────────────────────────────── │
     │          "OTP enviado com sucesso"            │
     │                                                │
     │  3. Recebe SMS com código                     │
     │                                                │
     │  4. Envia código para validação               │
     │ ───────────────────────────────────────────> │
     │                                                │
     │              5. Valida código                 │
     │                 Verifica expiração            │
     │                 Marca telefone verificado     │
     │ <─────────────────────────────────────────── │
     │          Retorna dados do cliente             │
     │          + token de sessão                    │
     │                                                │
```

## 📊 Diagrama de Estados - Mesa

```
┌─────────────┐
│ DISPONÍVEL  │ (Estado inicial inexistente - mesa criada já ocupada)
└─────────────┘
       │
       │ Cliente escaneia QR Code / Atendente cria mesa
       ↓
┌─────────────┐
│   OCUPADA   │ ← Mesa em uso, recebendo pedidos
└─────────────┘
       │
       │ Todos pedidos entregues
       ↓
┌─────────────────────┐
│ AGUARDANDO_PAGAMENTO│ ← Aguardando cliente pagar
└─────────────────────┘
       │
       │ Pagamento aprovado
       ↓
┌─────────────┐
│ FINALIZADA  │ → Mesa pode ser liberada
└─────────────┘
```

## 📊 Diagrama de Estados - Pedido

```
┌──────────┐
│ PENDENTE │ ← Pedido criado pelo cliente
└──────────┘
     │
     │ Atendente confirma
     ↓
┌──────────┐
│ RECEBIDO │ ← Pedido confirmado
└──────────┘
     │
     │ Começa preparo
     ↓
┌────────────┐
│ EM_PREPARO │ ← Sendo preparado na cozinha
└────────────┘
     │
     │ Preparo finalizado
     ↓
┌──────────┐
│  PRONTO  │ ← Pronto para servir
└──────────┘
     │
     │ Servido ao cliente
     ↓
┌──────────┐
│ ENTREGUE │ → Ciclo completo
└──────────┘

     │ (A qualquer momento antes de EM_PREPARO)
     ↓
┌───────────┐
│ CANCELADO │
└───────────┘
```

## 🎯 Regras de Negócio Críticas

### 1. Mesa sempre associada a Cliente
```java
// ❌ ERRADO - Mesa sem cliente
Mesa mesa = new Mesa();
mesa.setNumero(15);
mesaRepository.save(mesa); // ERRO!

// ✅ CORRETO - Mesa com cliente
Cliente cliente = clienteService.buscarPorTelefone("+5511999998888");
Mesa mesa = Mesa.builder()
    .numero(15)
    .cliente(cliente) // OBRIGATÓRIO
    .build();
```

### 2. Cliente único por mesa ativa
```java
// Sistema verifica automaticamente
mesaRepository.findMesaAtivaByClienteId(clienteId)
    .ifPresent(mesa -> {
        throw new BusinessException("Cliente já possui mesa ativa");
    });
```

### 3. Cálculo automático de totais
```java
// Pedido calcula automaticamente
pedido.calcularTotal(); // Soma todos os itens

// Mesa calcula automaticamente
mesa.calcularTotal(); // Soma todos os pedidos
```

## 🔔 Sistema de Notificações (WebSocket)

```
Tópicos WebSocket implementados:

/topic/pedidos/novos
  → Notifica atendentes quando novo pedido é criado

/topic/pedidos/{pedidoId}
  → Atualiza status específico de um pedido

/topic/mesas/{mesaId}
  → Atualiza informações da mesa em tempo real

/queue/user/{userId}
  → Mensagens privadas para usuário específico
```

## 💳 Integração com Gateway de Pagamento (Preparado)

```java
// Estrutura preparada no PagamentoService

// 1. Criar pagamento
Pagamento pagamento = pagamentoService.criar(request);
// → Sistema já salva transactionId, paymentUrl, qrCodePix

// 2. Gateway processa
// → Webhook recebe notificação
POST /api/pagamentos/webhook
{
  "transactionId": "abc123",
  "status": "approved"
}

// 3. Sistema atualiza status automaticamente
// → Notifica cliente via WebSocket
// → Atualiza status da mesa
```

## 🗂️ Padrões de Projeto Utilizados

### 1. Repository Pattern
- Abstração da camada de persistência
- Facilita testes e manutenção

### 2. Service Layer Pattern
- Lógica de negócio centralizada
- Transações gerenciadas

### 3. DTO Pattern
- Separação entre entidades e dados de transferência
- Validação na entrada, formatação na saída

### 4. Builder Pattern
- Construção fluente de objetos
- Código mais legível

### 5. Strategy Pattern
- Métodos de pagamento intercambiáveis
- Fácil adicionar novos métodos

## 🧪 Exemplos de Uso da API

### Criar produto
```bash
curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{
    "codigo": "PRATO005",
    "nome": "Lasanha Bolonhesa",
    "descricao": "Lasanha tradicional com molho bolonhesa",
    "preco": 45.90,
    "categoria": "PRATO_PRINCIPAL",
    "tempoPreparoMinutos": 30,
    "disponivel": true
  }'
```

### Criar pedido
```bash
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "mesaId": 1,
    "itens": [
      {
        "produtoId": 5,
        "quantidade": 2,
        "observacoes": "Sem cebola"
      },
      {
        "produtoId": 12,
        "quantidade": 1
      }
    ],
    "observacoes": "Cliente tem alergia a amendoim"
  }'
```

### Listar pedidos ativos
```bash
curl http://localhost:8080/api/pedidos/ativos
```

---

**Documentação técnica do Sistema de Restauração**
*Versão 1.0.0*
