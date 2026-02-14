# Guia de Execução de Testes

## ✅ PASSO 1: Testes E2E de Concorrência Real

### Executar via Maven
```bash
mvn test -Dtest=ConcurrencyRealE2ETest
```

### O que valida:
- ✅ 2 atendentes tentam marcar ENTREGUE simultaneamente → apenas 1 sucesso
- ✅ 2 cozinheiros tentam assumir() simultaneamente → apenas 1 sucesso  
- ✅ 10 threads criando pedidos simultaneamente → sem duplicação
- ✅ OptimisticLockException real via HTTP
- ✅ Isolation.SERIALIZABLE real

### Diferença CRÍTICA vs MockMvc:
- **MockMvc**: Threads compartilham contexto Spring (FALHA em concorrência)
- **TestRestTemplate + RANDOM_PORT**: Cada HTTP request = nova transação (CORRETO)

---

## ✅ PASSO 2: Controller Tests

### Executar todos os controller tests:
```bash
mvn test -Dtest=*ControllerTest
```

### Executar controller específico:
```bash
mvn test -Dtest=SubPedidoControllerTest
mvn test -Dtest=ProdutoControllerTest
```

### O que valida:
- ✅ Status HTTP corretos (200, 201, 400, 404, 409)
- ✅ Serialização/Deserialização JSON
- ✅ Validações de DTO
- ✅ Mensagens de erro adequadas
- ✅ Conflito de versão (409 CONFLICT)

### Controllers testados:
- [x] ProdutoController (8 testes) ✅
- [x] SubPedidoController (8 testes) ✅
- [ ] PedidoController (TODO)
- [ ] PagamentoController (TODO)
- [ ] MesaController (TODO)

---

## ✅ PASSO 3: Security Tests

### Executar:
```bash
mvn test -Dtest=SecurityPermissionsTest
```

### O que valida:
- ✅ CLIENTE não pode alterar estado de SubPedido
- ✅ ATENDENTE não pode assumir preparo
- ✅ COZINHA não pode entregar
- ✅ GERENTE pode cancelar em qualquer estado
- ✅ Transições inválidas lançam BusinessException

---

## ✅ PASSO 4: Teste de Carga

### Opção 1: Via JUnit (rápido)
```bash
# Descomentar @Disabled em LoadTest.java
mvn test -Dtest=LoadTest#testeCarga100ReqPorSegundoDurante60Segundos
```

### Opção 2: Via JMeter (recomendado para staging)

#### Instalar JMeter:
```bash
# Ubuntu/Debian
sudo apt install jmeter

# MacOS
brew install jmeter

# Windows
# Download: https://jmeter.apache.org/download_jmeter.cgi
```

#### Executar teste de carga:
```bash
# 1. Iniciar aplicação
mvn spring-boot:run

# 2. Em outro terminal, executar JMeter
jmeter -n -t load-test-plan.jmx -l results/results.jtl -e -o results/html-report

# 3. Abrir relatório
open results/html-report/index.html
```

### Opção 3: Via Apache Bench (alternativa rápida)
```bash
# 1000 requisições, 50 concorrentes
ab -n 1000 -c 50 -p pedido.json -T application/json http://localhost:8080/pedidos

# pedido.json:
# {"mesaId":1,"itens":[{"produtoId":1,"quantidade":1}]}
```

### O que valida:
- ✅ 100 req/s durante 60 segundos (6000 requisições)
- ✅ Taxa de sucesso > 97%
- ✅ Nenhum saldo negativo
- ✅ Nenhum pedido duplicado
- ✅ Integridade de dados mantida

---

## 📊 Validações Pós-Teste de Carga

### Queries SQL para validar integridade:

```sql
-- 1. Nenhum saldo negativo
SELECT * FROM fundo_consumo WHERE saldo < 0;
-- Resultado esperado: 0 registros

-- 2. Nenhum pedido duplicado
SELECT mesa_id, COUNT(*) 
FROM pedido 
WHERE created_at > NOW() - INTERVAL '5 minutes'
GROUP BY mesa_id
HAVING COUNT(*) > 10;
-- Resultado esperado: 0 registros (ou valores esperados)

-- 3. OptimisticLockException nos logs
grep "OptimisticLockException" logs/application.log | wc -l
-- Resultado esperado: > 0 (conflitos detectados e tratados)

-- 4. Consistência SubPedido
SELECT status, COUNT(*) FROM sub_pedido GROUP BY status;
-- Validar contadores fazem sentido
```

---

## 🚨 Antes de Sexta-Feira na Discoteca

### Checklist de Validação:

- [ ] **E2E Concorrência**: Todos passando ✅
- [ ] **Controller Tests**: Todos passando ✅  
- [ ] **Security Tests**: Todos passando ✅
- [ ] **Teste de Carga**: > 97% sucesso ✅
- [ ] **Validação SQL**: Nenhum saldo negativo ✅
- [ ] **Validação SQL**: Nenhum pedido duplicado ✅
- [ ] **Logs**: OptimisticLockException sendo tratado ✅

### Comando Único para Validar Tudo:
```bash
# Executar todos os testes (exceto @Disabled)
mvn clean test

# Ver relatório de cobertura
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## 📈 Monitoramento em Produção

### Métricas Críticas:
1. **Taxa de erro** < 3%
2. **Latência p95** < 500ms
3. **Throughput** > 100 req/s
4. **OptimisticLockException/min** < 10 (race conditions raras)

### Alertas Críticos:
- 🔴 Saldo negativo detectado
- 🔴 Taxa de erro > 5%
- 🔴 Latência p95 > 1s
- 🟡 OptimisticLockException/min > 20

---

## 🔧 Troubleshooting

### Teste falhando com timeout?
```bash
# Aumentar timeout no application.properties
spring.datasource.hikari.connection-timeout=30000
```

### JMeter não conecta?
```bash
# Verificar se aplicação está rodando
curl http://localhost:8080/actuator/health
```

### OptimisticLockException não aparece?
```bash
# Aumentar threads no teste E2E
# ConcurrencyRealE2ETest.java linha 45:
ExecutorService executor = Executors.newFixedThreadPool(10); // aumentar de 2 para 10
```

---

## 📚 Documentação Adicional

- [JMeter User Manual](https://jmeter.apache.org/usermanual/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [TestRestTemplate](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/web/client/TestRestTemplate.html)
