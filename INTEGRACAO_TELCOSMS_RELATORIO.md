# Módulo de Notificações TelcoSMS - Documentação

## 📱 Visão Geral

Módulo de notificações SMS integrado com o gateway **TelcoSMS** (https://www.telcosms.co.ao), seguindo o mesmo padrão de isolamento e qualidade usado na integração AppyPay.

---

## 🏗️ Arquitetura

### Estrutura de Pacotes

```
com.restaurante.notificacao/
├── enums/
│   ├── TipoNotificacao.java          # SMS, EMAIL, PUSH
│   └── StatusNotificacao.java        # PENDENTE, ENVIADA, FALHA, ENTREGUE
├── gateway/
│   └── telcosms/
│       ├── TelcoSmsProperties.java   # Configurações
│       ├── TelcoSmsClient.java       # Cliente HTTP
│       └── dto/
│           ├── TelcoSmsRequest.java  # Request DTO
│           └── TelcoSmsResponse.java # Response DTO
├── service/
│   └── NotificacaoService.java       # Lógica de negócio
└── controller/
    └── NotificacaoController.java    # API REST
```

---

## ⚙️ Configuração

### Perfil Padrão (Mock Mode)
```properties
# application.properties
app.notification.telcosms.base-url=https://www.telcosms.co.ao
app.notification.telcosms.api-key=${TELCOSMS_API_KEY:}
app.notification.telcosms.timeout-ms=15000
app.notification.telcosms.debug=false
app.notification.telcosms.mock=true  # ← Não envia SMS real
app.notification.telcosms.default-country-code=+244
app.notification.telcosms.max-retries=3
```

### Perfil DEV (Real Mode)
```properties
# application-dev.properties
app.notification.telcosms.base-url=https://www.telcosms.co.ao
app.notification.telcosms.api-key=prdb733efcb8c1dee91c3465281e4
app.notification.telcosms.mock=false  # ← Envia SMS real
app.notification.telcosms.debug=true
```

---

## 🚀 Como Usar

### Executar em Modo Mock (desenvolvimento local)
```bash
mvn spring-boot:run
```
📝 Logs mostrarão `[MOCK] SMS simulado para...`

### Executar em Modo Real (com API real)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
📡 Envia SMS real via TelcoSMS

---

## 📡 API TelcoSMS

### Endpoint Real
```
POST https://www.telcosms.co.ao/send_message
```

### Request Body
```json
{
  "message": {
    "api_key_app": "prdb733efcb8c1dee91c3465281e4",
    "phone_number": "244925813939",
    "message_body": "Mensagem de teste"
  }
}
```

### Response
```json
{
  "status": "success",
  "message": "SMS enviado com sucesso",
  "message_id": "MSG-12345"
}
```

---

## 🎯 Funcionalidades Implementadas

### 1. Normalização de Números de Telefone

Aceita múltiplos formatos:
- `925813939` → `244925813939`
- `0925813939` → `244925813939`
- `+244925813939` → `244925813939`
- `244925813939` → `244925813939`

### 2. Notificações Pré-Configuradas

#### OTP (Autenticação)
```java
notificacaoService.enviarOtp("+244925813939", "123456");
```
```
Seu código de verificação é: 123456
Válido por 5 minutos.
Sistema de Restauração
```

#### Recarga Confirmada
```java
notificacaoService.enviarNotificacaoRecargaConfirmada(
    "+244925813939", 5000.00, "GPO"
);
```
```
Recarga confirmada!
Valor: Kz 5000,00
Método: GPO
Sistema de Restauração
```

#### Pedido Criado
```java
notificacaoService.enviarNotificacaoPedidoCriado(
    "+244925813939", "PED-20260213-001", 2500.00
);
```
```
Pedido #PED-20260213-001 criado com sucesso!
Total: Kz 2500,00
Aguardando preparação.
Sistema de Restauração
```

#### Pedido Pronto
```java
notificacaoService.enviarNotificacaoPedidoPronto(
    "+244925813939", "PED-20260213-001"
);
```
```
Seu pedido #PED-20260213-001 está pronto! 🍴
Dirija-se ao balcão de retirada.
Sistema de Restauração
```

#### Referência Bancária (Multicaixa)
```java
notificacaoService.enviarNotificacaoReferenciaBancaria(
    "+244925813939", "12345", "999 888 777", 10000.00
);
```
```
Referência Multicaixa gerada:
Entidade: 12345
Referência: 999 888 777
Valor: Kz 10000,00
Válido por 24h.
Sistema de Restauração
```

#### Saldo Insuficiente
```java
notificacaoService.enviarNotificacaoSaldoInsuficiente(
    "+244925813939", 500.00, 2000.00
);
```
```
Saldo insuficiente!
Saldo atual: Kz 500,00
Necessário: Kz 2000,00
Recarregue seu fundo.
Sistema de Restauração
```

### 3. Sistema de Retry Automático

```java
// Tenta enviar até 3x com backoff exponencial
notificacaoService.enviarComRetry(
    telefone, mensagem, "CONTEXTO", 3
);
```

---

## 🧪 Endpoints REST

### Enviar SMS Genérico
```http
POST /api/notificacoes/sms
Content-Type: application/json

{
  "telefone": "+244925813939",
  "mensagem": "Mensagem de teste",
  "contexto": "TESTE"
}
```

### Enviar OTP
```http
POST /api/notificacoes/otp
Content-Type: application/json

{
  "telefone": "+244925813939",
  "codigo": "123456"
}
```

### Notificar Recarga Confirmada
```http
POST /api/notificacoes/recarga-confirmada
Content-Type: application/json

{
  "telefone": "+244925813939",
  "valor": 5000.00,
  "metodoPagamento": "GPO"
}
```

### Notificar Pedido Criado
```http
POST /api/notificacoes/pedido-criado
Content-Type: application/json

{
  "telefone": "+244925813939",
  "numeroPedido": "PED-20260213-001",
  "total": 2500.00
}
```

### Notificar Pedido Pronto
```http
POST /api/notificacoes/pedido-pronto
Content-Type: application/json

{
  "telefone": "+244925813939",
  "numeroPedido": "PED-20260213-001"
}
```

### Notificar Referência Bancária
```http
POST /api/notificacoes/referencia-bancaria
Content-Type: application/json

{
  "telefone": "+244925813939",
  "entidade": "12345",
  "referencia": "999 888 777",
  "valor": 10000.00
}
```

### Notificar Saldo Insuficiente
```http
POST /api/notificacoes/saldo-insuficiente
Content-Type: application/json

{
  "telefone": "+244925813939",
  "saldoAtual": 500.00,
  "valorNecessario": 2000.00
}
```

---

## 🔍 Logs e Monitoramento

### Modo Mock (Desenvolvimento)
```
📱 [MOCK] SMS simulado para 244925813939: Seu código é: 123456
INFO  - Notificação [OTP] enviada com sucesso - ID: MOCK-A3F2C8D1
```

### Modo Real (Produção)
```
DEBUG - Número normalizado: +244925813939 -> 244925813939
DEBUG - Enviando SMS via TelcoSMS para 244925813939: Seu código é: 123456
INFO  - SMS enviado com sucesso para 244925813939 - ID: MSG-REAL-12345
INFO  - Notificação [OTP] enviada com sucesso - ID: MSG-REAL-12345
```

### Logs de Erro
```
WARN  - Falha ao enviar notificação [OTP]: Insufficient credits
WARN  - Tentativa 1/3 falhou. Tentando novamente...
ERROR - Todas as 3 tentativas de envio falharam para 244925813939
```

---

## 🔐 Segurança

### Chave de API
- ✅ Armazenada em `application-dev.properties` (não versionada)
- ✅ Suporte a variável de ambiente: `${TELCOSMS_API_KEY}`
- ⚠️ **NUNCA** commitar chave real no repositório

### Modo Mock
- Protege contra envio acidental de SMS em desenvolvimento
- Simula responses sem custo
- Ideal para testes automatizados

---

## 📊 Estatísticas

### Arquivos Criados
- ✅ 8 novos arquivos Java
- ✅ Configurações em `application.properties`
- ✅ Configurações em `application-dev.properties`
- ✅ Endpoints de teste em `api-tests.http`

### Funcionalidades
- ✅ Envio de SMS via TelcoSMS
- ✅ Normalização de números de telefone
- ✅ Modo mock para desenvolvimento
- ✅ Sistema de retry automático
- ✅ 7 tipos de notificações pré-configuradas
- ✅ API REST completa
- ✅ Logs detalhados
- ✅ Tratamento de erros

---

## 🎓 Padrões Aplicados

### 1. Isolamento
Gateway completamente isolado da lógica de negócio

### 2. Configuração
Profiles Spring para ambientes diferentes (mock/real)

### 3. DTOs
Request/Response tipados para integração API

### 4. Logging
Logs estruturados com níveis apropriados

### 5. Tratamento de Erros
Try-catch com fallback e retry

### 6. Normalização
Processamento robusto de números de telefone

---

## 🚀 Próximos Passos (Opcional)

- [ ] Persistir histórico de notificações enviadas
- [ ] Dashboard de monitoramento de envios
- [ ] Suporte a templates de mensagens
- [ ] Integração com eventos do sistema (listeners)
- [ ] Testes automatizados (unit + integration)
- [ ] Rate limiting para evitar spam
- [ ] Blacklist de números

---

## 📝 Notas Importantes

1. **Chave de API Real**: `prdb733efcb8c1dee91c3465281e4`
2. **Número de Teste**: `+244925813939`
3. **URL Base**: `https://www.telcosms.co.ao`
4. **Timeout**: 15 segundos
5. **Retries**: 3 tentativas com backoff exponencial

---

## ✅ Status

**Implementação Completa** - Pronto para testes com API real no perfil DEV.

---

**Data**: 13 de fevereiro de 2026  
**Versão**: 1.0.0  
**Integração**: TelcoSMS Angola  
**Padrão**: ArenaTicket Production Grade
