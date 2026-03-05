# PERGUNTAS E DÚVIDAS — FRONTEND PARA BACKEND
**Data:** 05/03/2026  
**Contexto:** Análise completa do código frontend — pedidos, fundo de consumo, histórico, QR code, gestão de utilizadores, WebSocket e interface de cozinha.  
**Urgência:** Fluxos críticos bloqueados à espera de confirmação do backend.

---

## 1. HISTÓRICO DE PEDIDOS

### 1.1 Endpoint de listagem por sessão
O frontend chama `GET /api/pedidos/sessao-consumo/{sessaoConsumoId}` para carregar o histórico de pedidos de uma mesa.  
**Pergunta:** Este endpoint existe no backend? Qual o caminho correcto?

Alternativas conhecidas nos documentos:
- `GET /api/pedidos/sessao-consumo/{id}` — **não confirmado**
- `GET /api/pedidos/ativos` — apenas ativos, sem histórico
- `GET /api/pedidos/status/{status}` — requer filtro manual por status
- `GET /api/pedidos/unidade-consumo/{id}/historico` — mencionado num doc mas marcado como "fictício"

**Precisamos de:** um endpoint que retorne **todos os pedidos** (incluindo FINALIZADO/CANCELADO) de uma sessão específica.

### 1.2 Paginação
O endpoint de histórico retorna lista simples ou paginada (`Page<PedidoResponse>`)?  
Se paginado, qual é a estrutura? `{ content, totalElements, totalPages, page, size }` ?

### 1.3 Estrutura do `PedidoResponse`
Confirmar que o DTO inclui:
- `id`, `numero` (ex: "PED-001"), `status`, `statusFinanceiro`, `total`, `criadoEm`
- `subPedidos[]` com: `id`, `status`, `cozinha.nome`, `cozinha.tipo`, `itens[]`
- `itens[]` com: `id`, `produtoNome`, `quantidade`, `subtotal`, `observacoes`

---

## 2. PEDIDOS — FLUXO DE CRIAÇÃO

### 2.1 Payload do POST /pedidos
O frontend envia:
```json
{
  "sessaoConsumoId": 42,
  "tipoPagamento": "PRE_PAGO",
  "itens": [
    { "produtoId": 5, "quantidade": 2 },
    { "produtoId": 8, "quantidade": 1, "observacoes": "sem cebola" }
  ]
}
```
**Confirmar:** `observacoes` no item é campo aceite? É opcional?  
**Confirmar:** O campo `observacoes` existe também ao nível raiz do pedido (fora dos itens)?

### 2.2 PRE_PAGO — Débito do Fundo
Quando `tipoPagamento = PRE_PAGO`, o backend debita automaticamente o fundo do cliente (via `sessaoConsumoId → cliente → fundo`)?  
Precisamos confirmar: o frontend **não precisa** enviar `fundoConsumoId` no body?

### 2.3 Modo Anónimo com PRE_PAGO
Na sessão anónima (`modoAnonimo = true`), o pós-pago está bloqueado.  
O pré-pago anónimo é possível? Se sim, como? Via `tokenPortador` no body?

### 2.4 Resposta de erro 422
Quando o saldo do fundo é insuficiente, o backend retorna HTTP 422 com `{ message: "Saldo insuficiente..." }`?  
A mensagem no campo `message` é sempre legível pelo utilizador (em português)?

### 2.5 Observações gerais do pedido
Existe campo `observacoes` ao nível do pedido (não dos itens)?  
Ex: "Cliente alérgico a frutos secos — avisar cozinha"

---

## 3. SUB-PEDIDOS — FLUXO DA COZINHA

### 3.1 Quem pode executar cada transição?

| Endpoint | Role permitido |
|---|---|
| `PUT /subpedidos/{id}/assumir` | Só COZINHA? Ou também GERENTE? |
| `PUT /subpedidos/{id}/marcar-pronto` | Só COZINHA? Ou também GERENTE? |
| `PUT /subpedidos/{id}/marcar-entregue` | Só ATENDENTE? Ou também GERENTE? |
| `PUT /subpedidos/{id}/cancelar` | Só GERENTE? Ou também ADMIN? |

### 3.2 Estrutura do `SubPedidoResponse`
Confirmar que inclui:
```json
{
  "id": 1,
  "status": "PENDENTE | EM_PREPARACAO | PRONTO | ENTREGUE | CANCELADO",
  "cozinha": { "id": 1, "nome": "Grill", "tipo": "GRILL" },
  "itens": [
    { "id": 1, "produtoId": 5, "produtoNome": "Bife", "quantidade": 2, "subtotal": 5000, "observacoes": "..." }
  ],
  "criadoEm": "2026-03-05T10:00:00",
  "assumidoEm": "...",
  "prontoEm": "...",
  "entregueEm": "..."
}
```

### 3.3 Listagem de sub-pedidos ativos por cozinha
`GET /api/subpedidos/cozinha/{cozinhaId}/ativos` — retorna sub-pedidos com status `PENDENTE` e `EM_PREPARACAO` apenas?  
Ou inclui todos (PRONTO, ENTREGUE)?

### 3.4 Como o frontend COZINHA sabe o seu `cozinhaId`?
Quando um utilizador com role COZINHA faz login, o JWT ou o endpoint de perfil retorna o `cozinhaId` associado?  
O frontend precisa de fazer `GET /cozinhas` ou recebe o `cozinhaId` directamente no token/perfil?

---

## 4. INTERFACE DA COZINHA *(módulo não existe no frontend — a construir)*

O frontend **não tem nenhuma página de cozinha**. Este módulo precisa de ser construído de raiz.  
Para isso precisamos de confirmar os seguintes pontos:

### 4.1 Endpoint de listagem inicial
Quando o utilizador COZINHA entra na aplicação, qual endpoint usa para carregar os sub-pedidos pendentes da sua cozinha?
- `GET /subpedidos/cozinha/{cozinhaId}/ativos` ✓ (confirmado nos docs)

### 4.2 WebSocket para notificações em tempo real
O tópico STOMP `/topic/cozinha/{cozinhaId}` existe?  
Qual o payload enviado quando chega um novo sub-pedido?  
```json
// Exemplo esperado — confirmar estrutura real:
{
  "tipo": "NOVO_SUBPEDIDO",
  "subPedidoId": 42,
  "cozinhaId": 1,
  "pedidoNumero": "PED-015",
  "referenciaMesa": "Mesa 3",
  "itens": [...],
  "prioridade": "NORMAL"
}
```

### 4.3 Temas dos eventos WebSocket da cozinha
Quais os valores possíveis para o campo `tipo` (ou equivalente) nas mensagens do tópico `/topic/cozinha/{id}`?  
Ex: `NOVO_SUBPEDIDO`, `SUBPEDIDO_CANCELADO`, `PEDIDO_CANCELADO` — confirmar lista completa.

### 4.4 Áudio/alerta
Quando chega um novo sub-pedido à cozinha, o backend envia algum sinal especial ou o frontend é responsável por reproduzir o som?

### 4.5 Múltiplas cozinhas
Um utilizador COZINHA está sempre associado a **uma única** cozinha?  
Ou pode estar associado a múltiplas (e precisa de subscrever vários tópicos)?

---

## 5. WEBSOCKET — CONFIRMAÇÕES GERAIS

### 5.1 URL de ligação
O frontend liga a `ws://localhost:8080/ws` com SockJS + STOMP. Está correcto？  
Em produção, a URL muda para `wss://domínio/ws`? É via variável de ambiente?

### 5.2 Autenticação no WebSocket
A ligação STOMP precisa de enviar o JWT no header CONNECT?  
```javascript
// Implementação actual:
stompClient.connect({ Authorization: `Bearer ${token}` }, ...)
```
Está correcto? Ou a autenticação é feita de outra forma?

### 5.3 Tópico do atendente/unidade
O frontend subscreve `/topic/atendente/unidade/{unidadeId}` para receber alertas quando um sub-pedido fica PRONTO.  
**Confirmar:** Qual o payload exacto enviado neste tópico?  
```json
// Esperado — confirmar:
{
  "tipo": "SUBPEDIDO_PRONTO",
  "subPedidoId": 15,
  "pedidoId": 7,
  "referenciaMesa": "Mesa 3",
  "nomeProdutos": "Bife, Refrigerante"
}
```

### 5.4 Tópico de pedido individual
`/topic/pedido/{pedidoId}` — quais campos são enviados quando o status muda?  
O frontend precisa de re-fazer GET ou o WebSocket envia o objecto completo actualizado?

### 5.5 Reconexão automática
Em caso de queda de ligação, o backend aceita nova ligação com o mesmo token JWT sem problema?

---

## 6. FUNDO DE CONSUMO

### 6.1 Endpoint de listagem geral
A `FundosView` (página `/admin/fundos`) **não tem listagem geral** — começa vazia e o utilizador procura por telefone do cliente.  
Existe (ou pode existir) um endpoint `GET /fundos` ou `GET /fundos/lista` que retorne os fundos activos?  
Mesmo que paginado: `GET /fundos?page=0&size=20`  

Sem este endpoint, a gestão de fundos fica muito limitada para o GERENTE/ADMIN.

### 6.2 Campo `tipo` no `FundoConsumoResponse`
O DTO de fundo inclui um campo `tipo`? (Ex: `IDENTIFICADO` vs `ANONIMO`)?  
O frontend usa `fundo.tipo` para filtros mas não há confirmação deste campo.

### 6.3 Campos `totalRecarregado` e `totalConsumido`
Estes campos existem no `FundoConsumoResponse`?  
Actualmente o frontend exibe-os no modal de fundo de consumo.

### 6.4 Token portador (`tokenPortador`) — ciclo de vida
O `tokenPortador` muda quando:
- O fundo é transferido?
- A sessão é encerrada?
- Nunca muda, é permanente?

### 6.5 Fundo Anónimo — qual o `tokenPortador`?
Ao criar um fundo anónimo via `POST /fundos/anonimo/{token}`, o `{token}` é o token do QR Code da sessão?  
No frontend fazemos `fundoConsumoService.criarFundoAnonimo(sessao.qrCodeToken, saldoInicial)` — está correcto?

### 6.6 Histórico do fundo — estrutura da página
`GET /fundos/{token}/historico` retorna `Page<TransacaoFundoResponse>`.  
Quais os campos do `TransacaoFundoResponse`?
```json
// Esperado — confirmar:
{
  "id": 1,
  "tipo": "RECARGA | DEBITO | ESTORNO",
  "valor": 5000.00,
  "saldoAntes": 0.00,
  "saldoDepois": 5000.00,
  "descricao": "Recarga balcão",
  "criadoEm": "2026-03-05T10:00:00"
}
```

---

## 7. QR CODE

### 7.1 Resposta de `GET /qrcode/mesa/{mesaId}`
Retorna **array** ou **objecto único**?  
O frontend espera array: `buscarQrCodeMesa(mesaId)` → `response.data.data` (array).  
Se for objecto único, o frontend quebra.

### 7.2 Endpoint de imagem  
`GET /qrcode/imagem/{token}` — este endpoint existe e retorna imagem PNG directamente?  
Requer autenticação (header `Authorization: Bearer ...`)? Se sim, a tag `<img src="...">` não vai funcionar sem intervenção extra.

### 7.3 Endpoint `/qrcode/imagem/{token}/print`
Este sub-caminho `/print` existe para versão de impressão (500×500px)?

### 7.4 `POST /qrcode/renovar/{token}` — método HTTP
O frontend usa `POST /qrcode/renovar/{token}`. Está correcto ou é `PUT`?

### 7.5 Expiração do QR Code
O QR Code de mesa tem validade infinita (525600 minutos = 1 ano enviados pelo frontend)?  
Ou o backend ignora o `validadeMinutos` para mesas?

### 7.6 QR Code activo por mesa
Pode existir mais do que um QR Code activo por mesa ao mesmo tempo?  
Quando se renova, o antigo fica inactivo automaticamente?

---

## 8. GESTÃO DE UTILIZADORES

### 8.1 Estado de implementação
Os endpoints abaixo existem no backend?

| Endpoint | Estado |
|---|---|
| `GET /api/usuarios` | ❓ |
| `GET /api/usuarios/{id}` | ❓ |
| `POST /api/usuarios` | ❓ |
| `PUT /api/usuarios/{id}` | ❓ |
| `DELETE /api/usuarios/{id}` | ❓ |
| `PATCH /api/usuarios/{id}/ativar` | ❓ |
| `PATCH /api/usuarios/{id}/desativar` | ❓ |
| `PATCH /api/usuarios/{id}/senha` | ❓ |
| `GET /api/usuarios/permissoes` | ❓ |
| `GET /api/usuarios/{id}/logs` | ❓ |

### 8.2 Payload de criação de utilizador
Confirmar o body de `POST /api/usuarios`:
```json
{
  "nome": "João Silva",
  "telefone": "923456789",
  "senha": "senha123",
  "role": "ATENDENTE",
  "unidadeAtendimentoId": 1
}
```
O campo `email` é aceite/opcional?  
O `unidadeAtendimentoId` é obrigatório para ATENDENTE e COZINHA?

### 8.3 Role COZINHA
O `tipoUsuario` retornado no login pode ter valor `COZINHA`?  
Actualmente o frontend trata: ADMIN, GERENTE, ATENDENTE — **COZINHA não tem rota/página**.

### 8.4 Alterar senha (Admin reset)
`PATCH /api/usuarios/{id}/senha` com body `{ "novaSenha": "..." }` — está correcto?  
Ou é outro endpoint/formato?

### 8.5 Reset de senha por token (SMS/Email)
`POST /usuarios/reset-senha/confirmar` retorna HTTP 501 neste ambiente.  
Está planeado? Quando estará disponível? O frontend já tem o tratamento do 501.

### 8.6 Paginação de utilizadores
`GET /api/usuarios` aceita `page` e `size` como query params?  
Retorna `Page<UsuarioResponse>` ou lista simples?

### 8.7 Campos do `UsuarioResponse`
```json
// Confirmar campos retornados:
{
  "id": 1,
  "nome": "...",
  "telefone": "...",
  "email": "...",    // opcional?
  "tipoUsuario": "ADMIN | GERENTE | ATENDENTE | COZINHA",
  "ativo": true,
  "unidadeAtendimentoId": 1,
  "createdAt": "..."
}
```

---

## 9. AUTENTICAÇÃO E JWT

### 9.1 Estrutura do JWT
O JWT emitido por `POST /auth/admin/login` tem o campo `roles` como string simples (`"ROLE_ADMIN"`) ou array?  
O frontend actualmente trata string simples — confirmar se está correcto.

### 9.2 Campo `tipoUsuario` na resposta de login
A resposta de login inclui objeto `usuario` com `tipoUsuario`?  
```json
// Resposta actual esperada:
{
  "access_token": "eyJ...",
  "usuario": {
    "id": 1,
    "nome": "Admin",
    "tipoUsuario": "ADMIN",
    "telefone": "923000000"
  }
}
```
Ou o frontend deve extrair tudo do JWT?

### 9.3 Refresh token
Existe mecanismo de refresh token?  
Quando o JWT expira, o utilizador é redirecionado para login — está correcto este comportamento?

### 9.4 Duração do token
Qual a duração do JWT de admin? 8h? 24h? Configurável?

---

## 10. DASHBOARD

### 10.1 Campos do endpoint de top produtos
`GET /dashboard/relatorio` retorna campo `topProdutos` com qual estrutura?  
O frontend usa:
```javascript
produto.nomeProduto        // ou produto.nome?
produto.quantidadeVendida  // confirmado
produto.valorTotal         // confirmado
```
Confirmar os nomes exactos dos campos.

### 10.2 Período do relatório
O endpoint de dashboard aceita parâmetros de período? (`dataInicio`, `dataFim`)?  
Ou retorna sempre o período corrente (dia/semana/mês)?

---

## 11. CONFIGURAÇÕES FINANCEIRAS

### 11.1 PUT pos-pago/limite — body vs query param
O frontend envia: `PUT /configuracoes-financeiras/pos-pago/limite?novoLimite=5000&motivo=...`  
**Confirmar:** é query param ou body JSON?

### 11.2 Valor mínimo por operação
`PUT /configuracoes-financeiras/valor-minimo?novoValor=100&motivo=...`  
O `valorMinimoOperacao` aplica-se a: recargas? débitos? estornos? tudo?

### 11.3 `GET /configuracoes-financeiras` — campos do DTO
Confirmar todos os campos:
```json
{
  "posPagoAtivo": true,
  "limitePosPago": 10000.00,
  "valorMinimoOperacao": 100.00,
  "motivoUltimaAlteracao": "...",
  "atualizadoPorNome": "...",
  "atualizadoPorRole": "ADMIN",
  "updatedAt": "..."
}
```

---

## 12. UNIDADES DE ATENDIMENTO / MESAS

### 12.1 Campo `ativa` na mesa
`MesaResponse` tem campo `ativa` (booleano) para indicar mesa desactivada?  
O frontend usa `mesa.ativa === false` para grey-out do card — confirmar que este campo existe.

### 12.2 Campo `status` derivado
O `status` da mesa (`DISPONIVEL`, `OCUPADA`, `AGUARDANDO_PAGAMENTO`) é calculado pelo backend e enviado na resposta?  
O frontend **nunca** deriva este status localmente — confirmar.

### 12.3 `GET /mesas` vs `GET /unidades-consumo`
O frontend usa `mesasService.getTodas()` → `GET /mesas`.  
Existe também `GET /unidades-consumo`? São o mesmo recurso com dois paths?

---

## 13. FLUXOS NÃO IMPLEMENTADOS (NECESSITAM ATENÇÃO)

Os seguintes fluxos **existem no frontend mas não estão a funcionar** por falta de confirmação do backend:

| Funcionalidade | Estado Frontend | Bloqueio |
|---|---|---|
| Interface da Cozinha (CRUD sub-pedidos) | ❌ Módulo não existe | Precisa de ser construído do zero |
| Histórico de Pedidos por Sessão | ⚠️ Endpoint incerto | Confirmar `GET /pedidos/sessao-consumo/{id}` |
| Listagem Geral de Fundos | ❌ Não disponível | Pedir `GET /fundos?page=0&size=20` |
| Gestão de Utilizadores (CRUD) | ⚠️ Dependente do backend | Confirmar implementação endpoints |
| Reset de Senha por Token | ⚠️ Retorna 501 | Planeado? ETA? |
| Imagem QR Code via `<img>` | ⚠️ Pode falhar com auth | Confirmar se endpoint requer JWT |

---

## 14. PEDIDO DE INFORMAÇÃO ADICIONAL

### 14.1 Swagger / OpenAPI
Existe um Swagger UI disponível (ex: `http://localhost:8080/swagger-ui.html`)?  
Seria muito útil para o frontend validar todas as respostas sem depender de documentos.

### 14.2 Colecção Postman
Existe colecção Postman/Insomnia actualizada com todos os endpoints e payloads de exemplo?

### 14.3 Ambientes
- Dev local: `http://localhost:8080`
- Staging: qual URL?
- Produção: qual URL?

As variáveis de ambiente do frontend (`VITE_API_URL`, `VITE_WS_URL`) precisam de valores para staging/produção.

---

*Documento gerado automaticamente por análise do código fonte do frontend — 05/03/2026*
