# Guia de Implementação do Fluxo de Autenticação (OTP) para o Frontend

Bem-vindo(a) equipa do Front-end! 👋

Para que o fluxo de QR Ordering e a criação de Pedidos funcione perfeitamente, o Cliente precisa de estar autenticado com um JWT. Para isso, utilizamos o fluxo de verificação por OTP (One-Time Password).

Abaixo encontram o guia passo-a-passo corrigido de acordo com as validações de restrição (Body Fields) nativas da nossa API, de modo a evitarem erros de `400 BAD REQUEST` (Validation Failed) e `404 NOT FOUND`.

---

## Passo 1: Solicitar o Envio do OTP

A vossa tela de "Introduza o seu número de telemóvel" deverá invocar esta rota ao clicar em Avançar.

**ENDPOINT CORRETO API:**  
`POST /api/auth/solicitar-otp`

**HEADERS Obrigatórios:**
- `Content-Type: application/json`

**FORMATO DO BODY (JSON):**
```json
{
  "telefone": "92xxxxxxx"
}
```

**STATUS ESPERADO:**
- `200 OK`: Recebido com sucesso. O Backend vai disparar via SMS/Mocking o código (ex: `5142`). O Frontend deve agora renderizar o ecrã com os 4 dígitos para inserir o Código.

---

## Passo 2: Enviar o Código para Validação e Receber o JWT

*Atenção aos nomes das propriedades neste salto. O backend exige taxativamente a chave `"codigo"`!*

**ENDPOINT CORRETO API:**  
`POST /api/auth/validar-otp`

**HEADERS Obrigatórios:**
- `Content-Type: application/json`

**FORMATO DO BODY (JSON) — 🚨 ATENÇÃO AQUI:**
```json
{
  "telefone": "92xxxxxxx",
  "codigo": "5142"  
}
```
*(Nota: não metam "otp": "5142", tem de ser exatamente "codigo").*

**STATUS ESPERADO & RESPOSTA (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Autenticação realizada com sucesso",
  "data": {
    "username": "92xxxxxxx",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

**AÇÃO NO FRONTEND:** 
Ao receber a reposta `200 OK`, devem apanhar o `data.accessToken` e gravar o JWT localmente (no `AsyncStorage` / `LocalStorage`).

---

## Passo 3: Utilizar o JWT nos Endpoints Privados (Finalizar Fluxo)

A partir deste momento, todos os endpoints protegidos e pertencentes ao cenário do Cliente (*Ex: Criação da Sessão `iniciar-sessao`, Criação do `Pedido`, ou Completar `Perfil`*) exigem que injetem no cabeçalho HTTPS a Assinatura (Bearer).

**Exemplo num Pedido HTTP Fetch / Axios ao arrancar com a Sessão de Consumo QR:**

```javascript
fetch('http://URL_DO_SERVER/api/sessoes-consumo/cliente/iniciar-sessao/qr/mesa-12', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    // 🚨 AQUI ENTRA O TOKEN GUARDADO DO PASSO 2
    'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' 
  }
})
.then(response => response.json())
.then(data => console.log(data));
```

### Se não enviarem o Header (Authorization)...
A API vai fechar a porta com um `401 Unauthorized` (são as mensagens de erro `"Tentativa de acesso não autorizado: POST /api/pedidos/cliente"` que vocês têm visto no console log).

Bom código! Caso os campos `telefone` ou `codigo` vão a branco ou furem os tipos impostos, o backend retaliará com `400 BAD REQUEST` e a justificação virá no corpo do erro da ApiResponse.
