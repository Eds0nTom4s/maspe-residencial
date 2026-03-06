# ❓ QUESTÕES PARA BACKEND TEAM - WEBSOCKET

**Data:** 06/03/2026  
**Status:** WebSocket retornando HTTP 401 no handshake SockJS  
**URL testada:** `http://localhost:8080/api/ws`

---

## 🔴 PROBLEMA ATUAL

O frontend está recebendo **HTTP 401 Unauthorized** na requisição inicial do SockJS:

```
GET http://localhost:8080/api/ws/info?t=1772815863558
[HTTP/1.1 401  33ms]
```

### Logs do Console

```
🔌 Conectando ao WebSocket: http://localhost:8080/api/ws
🔑 Token disponível: eyJhbGciOiJIUzI1NiJ...
Opening Web Socket...
XHR GET http://localhost:8080/api/ws/info?token=...
[HTTP/1.1 401  33ms]
Connection closed to http://localhost:8080/api/ws
```

---

## ❓ QUESTÕES PARA O BACKEND

### 1. Configuração do Endpoint WebSocket

**Pergunta:** O endpoint `/api/ws` está configurado para permitir o handshake do SockJS sem autenticação?

**Contexto:** O SockJS faz uma requisição HTTP GET para `/api/ws/info` antes de estabelecer a conexão WebSocket. Esta requisição não pode enviar headers customizados (como `Authorization`).

**Configuração esperada no Spring Security:**

```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
            .antMatchers("/api/ws/**").permitAll()  // ← Permitir handshake SockJS
            .antMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
        .and()
        .csrf().disable();
}
```

**Questão:** A configuração acima está implementada?

---

### 2. Configuração do WebSocketMessageBrokerConfigurer

**Pergunta:** Como está configurado o registro do endpoint STOMP?

**Configuração esperada:**

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/api/ws")
        .setAllowedOriginPatterns("*")  // ou "http://localhost:3001" em dev
        .withSockJS();  // ← Habilita SockJS
}
```

**Questões:**
- O endpoint está registrado como `/api/ws`?
- O `withSockJS()` está habilitado?
- O CORS está permitindo `http://localhost:3001`?

---

### 3. Autenticação no Nível STOMP

**Pergunta:** Como o backend autentica as conexões STOMP após o handshake?

**Contexto:** Após o handshake HTTP do SockJS ser bem-sucedido (sem autenticação), o cliente STOMP envia o token JWT no header da mensagem CONNECT:

```javascript
stompClient.connect(
  { Authorization: 'Bearer ' + token },  // ← Header STOMP
  onConnectSuccess,
  onConnectError
);
```

**Configuração esperada no backend:**

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    // Validar token JWT aqui
                    // Setar principal/authentication
                }
            }
            return message;
        }
    });
}
```

**Questão:** Este interceptor está implementado no backend?

---

### 4. Alternativa: Token via Query Parameter

**Pergunta:** Se não for possível permitir o handshake sem autenticação, o backend suporta receber o token via query parameter?

**Exemplo:**
```
GET /api/ws/info?token=eyJhbGci...
```

**Configuração necessária no backend:**

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/api/ws")
        .setAllowedOriginPatterns("*")
        .setHandshakeHandler(new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(
                ServerHttpRequest request, 
                WebSocketHandler wsHandler, 
                Map<String, Object> attributes
            ) {
                // Extrair token do query parameter
                String query = request.getURI().getQuery();
                // Validar e retornar Principal
            }
        })
        .withSockJS();
}
```

**Questão:** Esta abordagem está implementada?

---

### 5. Logs do Backend

**Solicitação:** Podem partilhar os logs do backend quando a requisição `/api/ws/info` chega?

**Informações úteis:**
- Stack trace do erro 401
- Filtros de segurança que estão sendo aplicados
- Se a requisição está a chegar ao controller/handler

---

### 6. Versões e Dependências

**Questão:** Quais versões estão sendo usadas?

- Spring Boot: ?
- Spring Security: ?
- Spring WebSocket: ?
- Angular/mensageria: ?

**Dependências esperadas (Maven):**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-messaging</artifactId>
</dependency>
```

---

## 🎯 SOLUÇÃO RECOMENDADA

### Abordagem 1 (RECOMENDADA): Autenticação em Dois Níveis

1. **Handshake SockJS:** Permitir sem autenticação (`.permitAll()`)
2. **Conexão STOMP:** Autenticar via header `Authorization` no CONNECT

```java
// SecurityConfig.java
.antMatchers("/api/ws/**").permitAll()

// WebSocketConfig.java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new JwtChannelInterceptor(jwtUtils));
}
```

### Abordagem 2: Token no Query Parameter

Se a abordagem 1 não for viável, suportar token no query parameter do handshake:

```java
.setHandshakeHandler(new JwtHandshakeHandler(jwtUtils))
```

---

## 📋 CHECKLIST PARA BACKEND

- [ ] `/api/ws/**` está com `.permitAll()` no SecurityConfig?
- [ ] Endpoint STOMP registrado com `withSockJS()`?
- [ ] CORS configurado para aceitar `http://localhost:3001`?
- [ ] Interceptor STOMP valida token JWT do header?
- [ ] Logs do backend quando recebe `/api/ws/info`?

---

## 🔧 TESTE RÁPIDO NO BACKEND

Para confirmar que o endpoint está acessível, testar via cURL:

```bash
# Deve retornar 200 ou 204 (não 401)
curl -v http://localhost:8080/api/ws/info

# Resposta esperada:
# HTTP/1.1 200 OK
# Content-Type: application/json
# { "entropy": "...", "origins": [...], "cookie_needed": true, "websocket": true }
```

Se retornar 401, confirma que o handshake está bloqueado pelo Spring Security.

---

## 📞 CONTACTO

Para debugging em tempo real, podemos fazer:
1. Partilha de ecrã para ver logs do backend
2. Envio do `SecurityConfig.java` e `WebSocketConfig.java`
3. Teste conjunto com token específico

---

**Atualizado:** 06/03/2026  
**Frontend:** Vue 3 + SockJS + STOMP  
**Token JWT disponível:** ✅ (validado no login)
