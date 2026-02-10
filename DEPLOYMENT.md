# 🚀 Guia de Deployment e Produção

## Sistema de Restauração - Preparação para Ambiente de Produção

---

## 📋 Checklist Pré-Deploy

### Configurações Essenciais

#### 1. Variáveis de Ambiente
Nunca commitar senhas no código! Use variáveis de ambiente:

```properties
# application-prod.properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/restaurante_db}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}

# JWT Secret (quando implementado)
jwt.secret=${JWT_SECRET}

# OTP/SMS Config
otp.api.key=${OTP_API_KEY}
otp.api.url=${OTP_API_URL}

# Gateway de Pagamento
payment.gateway.api.key=${PAYMENT_API_KEY}
payment.gateway.api.secret=${PAYMENT_API_SECRET}
```

#### 2. Alterar DDL Strategy
```properties
# NUNCA usar "update" ou "create-drop" em produção!
spring.jpa.hibernate.ddl-auto=validate
```

#### 3. Desabilitar Logs Verbosos
```properties
spring.jpa.show-sql=false
logging.level.root=WARN
logging.level.com.restaurante=INFO
```

#### 4. Configurar CORS Corretamente
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

---

## 🐳 Docker

### Dockerfile
```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### docker-compose.yml
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: restaurante_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - restaurante-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/restaurante_db
      DB_USERNAME: postgres
      DB_PASSWORD: ${DB_PASSWORD}
      SPRING_PROFILES_ACTIVE: prod
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - restaurante-network
    restart: unless-stopped

volumes:
  postgres_data:

networks:
  restaurante-network:
    driver: bridge
```

### Executar com Docker
```bash
# Build
docker-compose build

# Iniciar
docker-compose up -d

# Logs
docker-compose logs -f app

# Parar
docker-compose down

# Parar e remover volumes (CUIDADO!)
docker-compose down -v
```

---

## ☁️ Deploy em Cloud

### AWS (EC2 + RDS)

#### 1. RDS PostgreSQL
```bash
# Criar instância RDS
aws rds create-db-instance \
    --db-instance-identifier restaurante-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 15 \
    --master-username admin \
    --master-user-password ${DB_PASSWORD} \
    --allocated-storage 20 \
    --backup-retention-period 7 \
    --publicly-accessible false
```

#### 2. EC2 Instance
```bash
# User data script para EC2
#!/bin/bash
yum update -y
yum install -y java-17-amazon-corretto docker
systemctl start docker
systemctl enable docker

# Baixar e executar aplicação
docker run -d \
  --name restaurante-api \
  -p 8080:8080 \
  -e DB_URL="${DB_URL}" \
  -e DB_USERNAME="${DB_USERNAME}" \
  -e DB_PASSWORD="${DB_PASSWORD}" \
  seu-registry/restaurante-api:latest
```

### Heroku
```bash
# Login
heroku login

# Criar app
heroku create restaurante-api

# Adicionar PostgreSQL
heroku addons:create heroku-postgresql:mini

# Deploy
git push heroku main

# Configurar variáveis
heroku config:set JWT_SECRET=seu_secret_aqui
heroku config:set OTP_API_KEY=sua_key_aqui

# Logs
heroku logs --tail
```

### Google Cloud Platform (Cloud Run)
```bash
# Build imagem
gcloud builds submit --tag gcr.io/seu-projeto/restaurante-api

# Deploy
gcloud run deploy restaurante-api \
  --image gcr.io/seu-projeto/restaurante-api \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars DB_URL=${DB_URL},DB_USERNAME=${DB_USERNAME},DB_PASSWORD=${DB_PASSWORD}
```

---

## 🔒 Segurança em Produção

### 1. HTTPS Obrigatório
```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
```

### 2. Implementar Rate Limiting
```java
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.of("api", RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .build());
    }
}
```

### 3. Configurar Security Headers
```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers()
                .contentSecurityPolicy("default-src 'self'")
                .and()
            .xssProtection()
                .and()
            .contentTypeOptions()
                .and()
            .frameOptions().deny();
        return http.build();
    }
}
```

### 4. Hash de Senhas (quando implementar)
```java
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

---

## 📊 Monitoramento

### 1. Spring Boot Actuator
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
```

### 2. Prometheus + Grafana
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
```

### 3. Logs Estruturados (Logback)
```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.json</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application-%d{yyyy-MM-dd}.json</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON" />
    </root>
</configuration>
```

---

## 🔄 CI/CD Pipeline

### GitHub Actions
```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Maven
      run: mvn clean package -DskipTests
    
    - name: Run Tests
      run: mvn test
    
    - name: Build Docker Image
      run: docker build -t restaurante-api:${{ github.sha }} .
    
    - name: Push to Registry
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker push restaurante-api:${{ github.sha }}
    
    - name: Deploy to Production
      run: |
        # SSH para servidor e atualizar container
        # ou usar AWS/GCP CLI
```

---

## 🗄️ Backup e Recuperação

### Script de Backup PostgreSQL
```bash
#!/bin/bash
# backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"
DB_NAME="restaurante_db"

# Backup
pg_dump -h localhost -U postgres $DB_NAME | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# Manter apenas últimos 7 dias
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +7 -delete

# Upload para S3 (opcional)
aws s3 cp $BACKUP_DIR/backup_$DATE.sql.gz s3://seu-bucket/backups/
```

### Restaurar Backup
```bash
# Restaurar do arquivo
gunzip -c backup_20260208_120000.sql.gz | psql -h localhost -U postgres restaurante_db

# Restaurar do S3
aws s3 cp s3://seu-bucket/backups/backup_20260208_120000.sql.gz - | gunzip | psql -h localhost -U postgres restaurante_db
```

---

## 🚨 Troubleshooting

### Problema: Out of Memory
```bash
# Aumentar memória JVM
JAVA_OPTS="-Xmx1024m -Xms512m"
```

### Problema: Connection Pool Esgotado
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

### Problema: Slow Queries
```properties
# Habilitar query logging
spring.jpa.properties.hibernate.generate_statistics=true
logging.level.org.hibernate.stat=DEBUG
```

---

## 📝 Checklist Final de Deploy

- [ ] Variáveis de ambiente configuradas
- [ ] DDL strategy = validate
- [ ] HTTPS habilitado
- [ ] CORS configurado corretamente
- [ ] Logs estruturados
- [ ] Backup automático configurado
- [ ] Monitoramento ativo (Actuator/Prometheus)
- [ ] Rate limiting implementado
- [ ] Security headers configurados
- [ ] Testes passando
- [ ] Documentação atualizada
- [ ] CI/CD pipeline funcionando
- [ ] Rollback plan definido
- [ ] Equipe treinada

---

**Guia de Deploy - Sistema de Restauração**  
*Versão 1.0.0 - Atualizado em 08/02/2026*
