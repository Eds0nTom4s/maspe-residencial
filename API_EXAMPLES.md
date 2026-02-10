# Coleção de Requisições - Sistema de Restauração

## 📌 Base URL
```
http://localhost:8080/api
```

---

## 🔐 Autenticação

### 1. Solicitar OTP
```http
POST /auth/solicitar-otp
Content-Type: application/json

{
  "telefone": "+5511999998888"
}
```

### 2. Validar OTP
```http
POST /auth/validar-otp
Content-Type: application/json

{
  "telefone": "+5511999998888",
  "codigo": "123456"
}
```

---

## 🪑 Mesas

### 3. Criar Mesa
```http
POST /mesas
Content-Type: application/json

{
  "numero": 15,
  "telefoneCliente": "+5511999998888",
  "qrCode": "MESA-15-ABC123",
  "capacidade": 4
}
```

### 4. Buscar Mesa por QR Code
```http
GET /mesas/qrcode/MESA-15-ABC123
```

### 5. Listar Mesas Abertas
```http
GET /mesas/abertas
```

### 6. Buscar Mesa por ID
```http
GET /mesas/1
```

### 7. Buscar Mesa Ativa do Cliente
```http
GET /mesas/cliente/1/ativa
```

### 8. Fechar Mesa
```http
PUT /mesas/1/fechar
```

---

## 🍽️ Produtos (Cardápio)

### 9. Criar Produto
```http
POST /produtos
Content-Type: application/json

{
  "codigo": "PRATO001",
  "nome": "Filé Mignon ao Molho Madeira",
  "descricao": "Filé mignon grelhado com molho madeira",
  "preco": 68.90,
  "categoria": "PRATO_PRINCIPAL",
  "tempoPreparoMinutos": 35,
  "disponivel": true
}
```

### 10. Listar Produtos Disponíveis
```http
GET /produtos
```

### 11. Listar Produtos por Categoria
```http
GET /produtos/categoria/PRATO_PRINCIPAL
```

### 12. Buscar Produtos por Nome
```http
GET /produtos/buscar?nome=filé
```

### 13. Atualizar Produto
```http
PUT /produtos/1
Content-Type: application/json

{
  "codigo": "PRATO001",
  "nome": "Filé Mignon ao Molho Madeira",
  "descricao": "Filé mignon grelhado com molho madeira e batatas",
  "preco": 72.90,
  "categoria": "PRATO_PRINCIPAL",
  "tempoPreparoMinutos": 35,
  "disponivel": true
}
```

### 14. Alterar Disponibilidade
```http
PATCH /produtos/1/disponibilidade?disponivel=false
```

### 15. Desativar Produto
```http
DELETE /produtos/1
```

---

## 📝 Pedidos

### 16. Criar Pedido
```http
POST /pedidos
Content-Type: application/json

{
  "mesaId": 1,
  "itens": [
    {
      "produtoId": 1,
      "quantidade": 2,
      "observacoes": "Sem cebola"
    },
    {
      "produtoId": 5,
      "quantidade": 1
    },
    {
      "produtoId": 10,
      "quantidade": 2
    }
  ],
  "observacoes": "Cliente tem alergia a amendoim"
}
```

### 17. Buscar Pedido por ID
```http
GET /pedidos/1
```

### 18. Buscar Pedido por Número
```http
GET /pedidos/numero/PED-20260208-001
```

### 19. Listar Pedidos da Mesa
```http
GET /pedidos/mesa/1
```

### 20. Listar Pedidos por Status
```http
GET /pedidos/status/PENDENTE
```

### 21. Listar Pedidos Ativos
```http
GET /pedidos/ativos
```

### 22. Atualizar Status do Pedido
```http
PATCH /pedidos/1/status?status=RECEBIDO
```

### 23. Avançar Status do Pedido
```http
PUT /pedidos/1/avancar
```

### 24. Cancelar Pedido
```http
PUT /pedidos/1/cancelar
```

---

## 💳 Pagamentos

### 25. Criar Pagamento
```http
POST /pagamentos
Content-Type: application/json

{
  "mesaId": 1,
  "valor": 125.70,
  "metodoPagamento": "PIX",
  "observacoes": "Pagamento via PIX"
}
```

### 26. Buscar Pagamento por ID
```http
GET /pagamentos/1
```

### 27. Buscar Pagamento da Mesa
```http
GET /pagamentos/mesa/1
```

### 28. Aprovar Pagamento
```http
PUT /pagamentos/1/aprovar
```

### 29. Recusar Pagamento
```http
PUT /pagamentos/1/recusar?motivo=Cartão recusado
```

### 30. Cancelar Pagamento
```http
PUT /pagamentos/1/cancelar?motivo=Cliente desistiu
```

### 31. Webhook do Gateway
```http
POST /pagamentos/webhook?transactionId=abc123&status=approved
```

---

## 📊 Fluxo Completo de Teste

### Cenário: Cliente faz pedido e paga

```bash
# 1. Solicitar OTP
curl -X POST http://localhost:8080/api/auth/solicitar-otp \
  -H "Content-Type: application/json" \
  -d '{"telefone": "+5511999998888"}'

# 2. Validar OTP (usar código recebido)
curl -X POST http://localhost:8080/api/auth/validar-otp \
  -H "Content-Type: application/json" \
  -d '{"telefone": "+5511999998888", "codigo": "123456"}'

# 3. Criar Mesa
curl -X POST http://localhost:8080/api/mesas \
  -H "Content-Type: application/json" \
  -d '{
    "numero": 15,
    "telefoneCliente": "+5511999998888",
    "qrCode": "MESA-15-ABC123"
  }'

# 4. Listar Produtos
curl http://localhost:8080/api/produtos

# 5. Criar Pedido
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{
    "mesaId": 1,
    "itens": [
      {"produtoId": 1, "quantidade": 1},
      {"produtoId": 5, "quantidade": 2}
    ]
  }'

# 6. Atendente avança status do pedido
curl -X PUT http://localhost:8080/api/pedidos/1/avancar

# 7. Criar Pagamento
curl -X POST http://localhost:8080/api/pagamentos \
  -H "Content-Type: application/json" \
  -d '{
    "mesaId": 1,
    "valor": 125.70,
    "metodoPagamento": "DINHEIRO"
  }'

# 8. Aprovar Pagamento
curl -X PUT http://localhost:8080/api/pagamentos/1/aprovar

# 9. Fechar Mesa
curl -X PUT http://localhost:8080/api/mesas/1/fechar
```

---

## 🧪 Variáveis de Ambiente Sugeridas

Para Postman/Insomnia:

```json
{
  "base_url": "http://localhost:8080/api",
  "telefone_teste": "+5511999998888",
  "mesa_id": "1",
  "pedido_id": "1",
  "produto_id": "1",
  "pagamento_id": "1"
}
```

---

## 📌 Status Codes Esperados

- `200 OK` - Requisição bem-sucedida
- `201 Created` - Recurso criado com sucesso
- `400 Bad Request` - Dados inválidos ou erro de negócio
- `404 Not Found` - Recurso não encontrado
- `500 Internal Server Error` - Erro interno do servidor

---

## 🔔 Categorias de Produtos

Valores válidos para `categoria`:

- `ENTRADA`
- `PRATO_PRINCIPAL`
- `ACOMPANHAMENTO`
- `SOBREMESA`
- `BEBIDA_ALCOOLICA`
- `BEBIDA_NAO_ALCOOLICA`
- `LANCHE`
- `PIZZA`
- `OUTROS`

---

## 💰 Métodos de Pagamento

Valores válidos para `metodoPagamento`:

- `DINHEIRO`
- `CARTAO_CREDITO`
- `CARTAO_DEBITO`
- `PIX`
- `VALE_REFEICAO`
- `DIGITAL`

---

**Collection completa para testes da API**
*Sistema de Restauração v1.0.0*
