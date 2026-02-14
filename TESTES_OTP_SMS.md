# 🧪 SCRIPT DE TESTES - Sistema de Restauração

## ✅ **INTEGRAÇÃO 1: OTP com TelcoSMS**

### Passo 1: Solicitar OTP
```bash
curl -X POST http://localhost:8080/api/auth/solicitar-otp \
  -H "Content-Type: application/json" \
  -d '{
    "telefone": "+244925813939"
  }'
```

**Resultado Esperado:**
```json
{
  "success": true,
  "message": "OTP enviado com sucesso",
  "data": null
}
```

**Log esperado no servidor:**
```
INFO  - Solicitando OTP para telefone: +244925813939
INFO  - NotificacaoService inicializado com gateway: TelcoSMS
INFO  - Enviando notificação SMS [OTP] para 244925813939 via TelcoSMS
INFO  - 📱 [MOCK] SMS simulado via TelcoSMS para 244925813939: Seu código de verificação é: 1234
INFO  - OTP 1234 enviado com sucesso para +244925813939 (válido por 5 minutos)
```

---

### Passo 2: Validar OTP
```bash
curl -X POST http://localhost:8080/api/auth/validar-otp \
  -H "Content-Type: application/json" \
  -d '{
    "telefone": "+244925813939",
    "codigo": "1234"
  }'
```

**Resultado Esperado:**
```json
{
  "success": true,
  "message": "Autenticação realizada com sucesso",
  "data": {
    "id": 1,
    "telefone": "+244925813939",
    "nome": null,
    "telefoneVerificado": true,
    "ativo": true,
    "createdAt": "2026-02-14T17:00:00"
  }
}
```

---

## ✅ **INTEGRAÇÃO 2: Notificações Diretas**

### Teste 1: Enviar OTP Manualmente
```bash
curl -X POST http://localhost:8080/notificacoes/otp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_JWT" \
  -d '{
    "telefone": "+244925813939",
    "codigo": "5678",
    "validadeMinutos": 5
  }'
```

### Teste 2: Notificar Recarga Confirmada
```bash
curl -X POST http://localhost:8080/notificacoes/recarga-confirmada \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_JWT" \
  -d '{
    "telefone": "+244925813939",
    "valor": 150.00,
    "metodoPagamento": "GPO"
  }'
```

### Teste 3: Notificar Pedido Criado
```bash
curl -X POST http://localhost:8080/notificacoes/pedido-criado \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_JWT" \
  -d '{
    "telefone": "+244925813939",
    "numeroPedido": "001",
    "total": 85.50
  }'
```

---

## ✅ **FLUXO COMPLETO**

### 1. Login JWT (para obter token)
```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

Copiar o `token` da resposta.

---

### 2. Listar Produtos (com token)
```bash
curl -X GET http://localhost:8080/produtos \
  -H "Authorization: Bearer SEU_TOKEN_AQUI"
```

---

### 3. Criar Mesa
```bash
curl -X POST http://localhost:8080/api/unidades-consumo \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" \
  -d '{
    "tipo": "MESA",
    "identificador": "MESA-05",
    "capacidade": 4,
    "telefone": "+244925813939",
    "unidadeAtendimentoId": 1
  }'
```

---

### 4. Criar Pedido
```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" \
  -d '{
    "unidadeDeConsumoId": 1,
    "itens": [
      {
        "produtoId": 1,
        "quantidade": 2,
        "observacao": "Sem cebola"
      }
    ],
    "observacao": "Pedido teste"
  }'
```

---

## 🔍 **VERIFICAÇÕES**

### Verificar Log do Servidor
```bash
tail -f nohup.out | grep -E "OTP|SMS|Notificacao"
```

### Verificar se Aplicação Está Rodando
```bash
curl http://localhost:8080/actuator/health
```

### Verificar Perfil Ativo
```bash
curl http://localhost:8080/actuator/env | grep "spring.profiles.active"
```

---

## 📊 **CHECKLIST DE TESTES**

- [ ] Aplicação iniciou sem erros
- [ ] Perfil DEV ativo (mock=false para TelcoSMS)
- [ ] Solicitar OTP funciona
- [ ] SMS é enviado (ou log de mock aparece)
- [ ] Validar OTP funciona
- [ ] Cliente é autenticado
- [ ] Notificações manuais funcionam
- [ ] JWT login funciona
- [ ] Endpoints protegidos exigem token
- [ ] Logs mostram "TelcoSMS" como provider

---

## 🐛 **TROUBLESHOOTING**

### Erro: "Access Denied"
→ Fazer login JWT e adicionar header `Authorization: Bearer TOKEN`

### Erro: "OTP inválido ou expirado"
→ Código expira em 5 minutos, solicitar novo OTP

### SMS não enviado
→ Verificar perfil DEV ativo e mock=false
→ Verificar api-key do TelcoSMS

### Erro de compilação
→ `mvn clean compile -DskipTests`
