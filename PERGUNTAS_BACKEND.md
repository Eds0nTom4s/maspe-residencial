================================================================================
  CARTA AO BACKEND — PERGUNTAS E LACUNAS DE CONTRATO
  Emitido por: Equipe Frontend
  Data: 01 de Março de 2026
  Documento de referência: ALINHAMENTO_FRONTEND.txt (01/03/2026)
================================================================================

Esta carta foi gerada após revisão completa do código frontend.
Cada ponto é uma LACUNA real encontrada no código que impede integração correcta.
Organizada por prioridade: 🔴 Bloqueante · 🟡 Importante · 🔵 Clarificação

________________________________________________________________________________
ÍNDICE
________________________________________________________________________________

  SECÇÃO 1 — Autenticação (Login/JWT)               [3 perguntas — 🔴🔴🟡]
  SECÇÃO 2 — Fundo de Consumo / Modo Anónimo        [4 perguntas — 🔴🔴🟡🔵]
  SECÇÃO 3 — Pedidos                                [5 perguntas — 🔴🟡🟡🔵🔵]
  SECÇÃO 4 — Auditoria                              [2 perguntas — 🟡🟡]
  SECÇÃO 5 — Dashboard                              [2 perguntas — 🟡🟡]
  SECÇÃO 6 — Produtos                               [1 pergunta  — 🟡]
  SECÇÃO 7 — Configurações Financeiras               [1 pergunta  — 🔵]
  SECÇÃO 8 — Unidades de Consumo                    [2 perguntas — 🔵🔵]
  SECÇÃO 9 — WebSocket / Tempo Real                 [1 pergunta  — 🟡]
  SECÇÃO 10 — Roles / Permissões                    [1 pergunta  — 🟡]

________________________________________________________________________________
SECÇÃO 1 — AUTENTICAÇÃO (LOGIN / JWT)
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔴 PERGUNTA 1.1 — Qual é o endpoint ÚNICO e correcto de login?         │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend tem TRÊS referências a endpoints de login diferentes no mesmo
  código-base. Todas as três são actualmente importadas ou executadas:

    a) POST /api/auth/admin/login   →  body: { telefone, senha }
       → Usado em: src/store/auth.js (Pinia store — É O QUE ESTÁ EM USO)

    b) POST /api/auth/jwt/login     →  body: { username, password }
       → Referenciado em: src/services/authService.js (NÃO usado actualmente)

    c) POST /api/auth/login         →  body: { email, password }
       → Definido em: src/services/api.js (inline, NÃO usado actualmente)

  A Pinia store usa o endpoint (a) e tem comentário:
    "Estrutura REAL do backend (diferente da documentação)"

  PERGUNTA: Qual é o endpoint correcto e definitivo de login?
  E qual a estrutura exacta do body e da resposta?

  Resposta esperada (exemplo):
    Endpoint: POST /api/auth/admin/login
    Body:     { "telefone": "+244912345678", "senha": "xxxx" }
    Response: {
      "success": true,
      "data": {
        "id": 1,
        "nome": "Nome do utilizador",
        "telefone": "+244912345678",
        "email": "...",
        "tipoUsuario": "ADMIN",       ← ou "roles": ["ROLE_ADMIN"]?
        "token": "eyJ...",
        "expiresIn": 3600000,
        "unidadeAtendimentoId": 1     ← presente para ATENDENTE/GERENTE?
      }
    }

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔴 PERGUNTA 1.2 — Quais são os CLAIMS exactos do JWT payload?          │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend usa auth.js → `checkAuth()` para restaurar sessão a partir do
  token JWT armazenado. Actualmente tenta ler:

    payload.userId || payload.sub          → ID do utilizador
    payload.nome || payload.name           → Nome
    payload.email                          → Email
    payload.roles                          → Array de roles (ex: ["ROLE_ADMIN"])
    payload.unidadeAtendimentoId           → ID da unidade de atendimento

  PERGUNTA: Confirmar os nomes exactos dos claims JWT.
  Em particular:
    - O ID do utilizador está em `sub` ou `userId`?
    - O nome está em `nome` ou `name`?
    - As roles estão em `roles` (array) e qual o formato? ["ROLE_ADMIN"] ou ["ADMIN"]?
    - O `unidadeAtendimentoId` existe no JWT para ATENDENTE/GERENTE?
    - Para ADMIN (sem unidade), o campo existe como null ou é omitido?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 1.3 — Existe endpoint para validar/refrescar o token?      │
  └─────────────────────────────────────────────────────────────────────────┘

  O `api.js` tem uma referência a `POST /api/auth/refresh` e `GET /api/auth/me`
  mas nenhum dos dois está em uso real. A `checkAuth()` faz decodificação local
  do JWT sem chamada ao servidor.

  PERGUNTA:
    a) Existe `GET /api/auth/me` (retornar dados do utilizador autenticado)?
    b) Existe `POST /api/auth/refresh` para renovar token antes de expirar?
    c) Se existirem, qual a estrutura de resposta de cada um?


________________________________________________________________________________
SECÇÃO 2 — FUNDO DE CONSUMO / MODO ANÓNIMO
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔴 PERGUNTA 2.1 — Como funciona o PRE_PAGO em unidades ANÓNIMAS?       │
  │    (BLOQUEANTE — impossível criar pedido sem esta informação)           │
  └─────────────────────────────────────────────────────────────────────────┘

  O ALINHAMENTO_FRONTEND.txt §5-B diz:
    "fundo de consumo keyed pelo UUID do QR Code"

  Mas NÃO descreve nenhum endpoint para:
    1. Criar um fundo para uma unidade anónima (via QR code UUID)
    2. Consultar o saldo desse fundo
    3. Recarregar esse fundo

  O frontend actualmente usa `buscarFundoPorCliente(clienteId)` para PRE_PAGO.
  Para unidades anónimas, `cliente` é sempre `null` — logo esta chamada nunca
  acontece.

  Após a revisão, corrigimos a lógica para permitir criar o pedido PRE_PAGO
  em modo anónimo (o backend processa), mas o frontend NÃO consegue:
    - Mostrar o saldo do fundo na unidade anónima
    - Avisar o operador quando o saldo está baixo
    - Oferecer o botão "Recarregar" para unidades anónimas

  PERGUNTAS:
    a) Qual o endpoint para BUSCAR O FUNDO de uma unidade anónima?
       Exemplo esperado: GET /api/fundos/unidade-consumo/{unidadeConsumoId}
                     ou: GET /api/fundos/qr/{qrCode}

    b) Qual o endpoint para CRIAR UM FUNDO para uma unidade anónima?
       Ou o fundo é criado automaticamente quando a unidade é criada?

    c) Qual o endpoint para RECARREGAR o fundo de uma unidade anónima?
       Mantém o mesmo `POST /api/fundos/{fundoId}/recarregar`?

    d) O fundo de uma unidade anónima fica associado ao `id` da unidade
       ou ao `qrCode` (UUID)?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔴 PERGUNTA 2.2 — Existe endpoint de LISTAGEM de todos os fundos?      │
  └─────────────────────────────────────────────────────────────────────────┘

  A `FundosView.vue` tem comentário no código:
    "Backend não tem endpoint de listagem completa. Lista começa vazia."

  Actualmente o operador precisa pesquisar um fundo por ID de cliente
  manualmente — não há visão geral.

  PERGUNTA: Existe ou está planeado `GET /api/fundos`?
  Se sim:
    - Quais os parâmetros de filtro (status, unidadeAtendimentoId, page, size)?
    - A listagem inclui fundos de unidades anónimas?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 2.3 — O endpoint /api/fundos/config/valor-minimo existe?   │
  └─────────────────────────────────────────────────────────────────────────┘

  O `fundoConsumoService.js` chama `GET /api/fundos/config/valor-minimo`
  para obter o valor mínimo de recarga.

  O ALINHAMENTO diz que esse valor agora está em:
    GET /api/configuracoes-financeiras → campo `valorMinimoOperacao`

  PERGUNTA:
    a) O endpoint `GET /api/fundos/config/valor-minimo` ainda existe?
    b) Deve ser substituído pelo campo `valorMinimoOperacao` do GET config?
    c) Qual a fonte de verdade para o valor mínimo de operação?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 2.4 — Gateway AppyPay (GPO/REF) — estado actual            │
  └─────────────────────────────────────────────────────────────────────────┘

  O `fundoConsumoService.recarregarFundo()` usa:
    POST /api/fundos/{fundoId}/recarregar
    Body: { valor, metodoPagamento: "GPO" | "REF", descricao }

  PERGUNTAS:
    a) Este endpoint está activo e funcional em ambiente de staging/produção?
    b) Para recarregas em modo de desenvolvimento/testes, há algum método
       de pagamento de teste (sem invocar o gateway real)?
    c) O polling de confirmação (`aguardarConfirmacaoPagamento`) ainda é
       a abordagem correcta ou existe endpoint de webhook/callback?


________________________________________________________________________________
SECÇÃO 3 — PEDIDOS
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔴 PERGUNTA 3.1 — finalizar vs fechar — são operações diferentes?      │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend tem dois métodos com nomes diferentes que parecem fazer o mesmo:

    pedidosBalcaoService.finalizar(id)  →  POST /api/pedidos/{id}/finalizar
    pedidoService.fechar(pedidoId, ...) →  pedidoApi.fechar() (método interno)

  E `pedido.service.fechar()` exige `formaPagamento` como parâmetro,
  implicando que é o acto de confirmar o pagamento.

  PERGUNTAS:
    a) Qual o endpoint exacto de `pedidoApi.fechar()`?
       É `POST /api/pedidos/{id}/fechar` ou `PUT /api/pedidos/{id}/fechar`?
    b) `finalizar` e `fechar` são a mesma operação?
    c) Se diferentes: qual o fluxo correcto?
       Exemplo:  CRIADO → fechar (confirmar pagamento) → FINALIZADO
             ou: CRIADO → EM_ANDAMENTO → finalizar → FINALIZADO

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 3.2 — cancelar usa POST ou PUT? Body ou query param?       │
  └─────────────────────────────────────────────────────────────────────────┘

  Existem dois serviços de pedidos que cancelam com métodos HTTP diferentes:

    pedidosBalcaoService.cancelar(id, motivo):
      → POST /api/pedidos/{id}/cancelar     body: { motivo }

    pedidos.js.cancelar(id, motivo):
      → PUT  /api/pedidos/{id}/cancelar     query param: ?motivo=...

  PERGUNTA: Qual é o método HTTP e o formato do `motivo` correcto?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 3.3 — O campo `origem` é obrigatório ou opcional?          │
  └─────────────────────────────────────────────────────────────────────────┘

  O `pedido.service.criar()` envia `origem: dados.origem || 'BALCAO'`.
  Os valores assumidos são: `BALCAO`, `QRCODE`, `APP`.

  PERGUNTAS:
    a) O campo `origem` existe no `CriarPedidoRequest`?
    b) Quais os valores aceites pelo enum?
    c) Se omitido, o backend usa default ou retorna 400?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 3.4 — O endpoint POST /api/pagamentos existe?              │
  └─────────────────────────────────────────────────────────────────────────┘

  O `pedidosBalcaoService.processarPagamento()` chama:
    POST /api/pagamentos
    Body: { pedidoId, metodoPagamento, valorPago }

  Este endpoint não aparece no ALINHAMENTO_FRONTEND.txt.

  PERGUNTAS:
    a) Este endpoint existe e está activo?
    b) Quando é usado (qual o fluxo que o invoca)?
    c) O `metodoPagamento` aceita quais valores? ("DINHEIRO", "CARTAO", etc.)

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 3.5 — O campo `fundoConsumoId` vem na resposta do pedido?  │
  └─────────────────────────────────────────────────────────────────────────┘

  O `pedido.service.fechar()` verifica `pedido.data.fundoConsumoId`:
    if (!pedido.data.fundoConsumoId) throw BusinessRuleError(...)

  PERGUNTA: O `PedidoResponse` inclui o campo `fundoConsumoId`?
  Se não, esta validação deve ser removida para evitar bloqueio de pagamento.


________________________________________________________________________________
SECÇÃO 4 — AUDITORIA
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 4.1 — Quais endpoints de auditoria estão disponíveis?      │
  └─────────────────────────────────────────────────────────────────────────┘

  O ALINHAMENTO_FRONTEND.txt §7 diz:
    "Consultar a equipa de backend para obter o endpoint REST de listagem
     de eventos (a publicar na próxima sprint)."

  O `auditoriaService.js` tem 404-fallback porque os endpoints não existem.
  Os endpoints assumidos (todos não confirmados) são:

    GET  /api/auditoria/logs                             ← principal
    GET  /api/auditoria/logs/{id}
    GET  /api/auditoria/usuarios/{usuarioId}/logs
    GET  /api/auditoria/entidades/{tipo}/{id}
    GET  /api/auditoria/estatisticas
    GET  /api/auditoria/modulos
    GET  /api/auditoria/acoes
    GET  /api/auditoria/logs/exportar/csv
    GET  /api/auditoria/logs/exportar/pdf

  PERGUNTAS:
    a) Quais destes endpoints estão implementados (ou em que sprint chegam)?
    b) Qual a estrutura de paginação? (page/size? page 0-based ou 1-based?)
    c) Qual a estrutura do objecto `AuditoriaLog` retornado?
    d) A tabela `configuracao_financeira_event_log` mencionada no §7 é
       consultada pela mesma API ou por endpoint separado?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 4.2 — Quais `módulos` e `acções` existem para filtrar?     │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend tem fallback hardcoded de módulos e acções (caso 404):
    Módulos:  PEDIDOS, PRODUTOS, USUARIOS, FUNDOS, MESAS, CONFIGURACOES, AUTENTICACAO
    Acções:   LOGIN, LOGOUT, CRIAR, EDITAR, EXCLUIR, ATIVAR, DESATIVAR, PAGAMENTO,
              ESTORNO, CANCELAMENTO

  PERGUNTA: Estes valores correspondem aos do backend? Há outros?


________________________________________________________________________________
SECÇÃO 5 — DASHBOARD
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 5.1 — Os endpoints de dashboard existem?                   │
  └─────────────────────────────────────────────────────────────────────────┘

  O `dashboardService.js` usa:
    GET /api/dashboard/stats
    GET /api/dashboard/activity
    GET /api/dashboard/top-products

  Estes endpoints NÃO constam no ALINHAMENTO_FRONTEND.txt.

  PERGUNTAS:
    a) Estes endpoints existem?
    b) Qual a estrutura de resposta de cada um?
       O frontend espera de `stats`:
         { totalPedidosHoje, pedidosEmAndamento, pedidosConcluidos,
           pedidosCancelados, receitaHoje, receitaMes,
           mesasOcupadas, mesasDisponiveis, clientesAtivos, produtosDisponiveis }
    c) Há restrição de role para aceder ao dashboard?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 5.2 — Os status de pedido são CRIADO, EM_ANDAMENTO,        │
  │    FINALIZADO, CANCELADO? (para badges de cor no dashboard)             │
  └─────────────────────────────────────────────────────────────────────────┘

  O dashboard usa os seguintes valores de status para atribuir cor:
    CRIADO → azul · EM_ANDAMENTO → amarelo · FINALIZADO → verde · CANCELADO → vermelho

  PERGUNTA: Estes são os valores exactos do enum `StatusPedido`?
  Existe `CONFIRMADO` ou outros status?


________________________________________________________________________________
SECÇÃO 6 — PRODUTOS
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 6.1 — Upload de imagem de produto: endpoint e resposta     │
  └─────────────────────────────────────────────────────────────────────────┘

  O `produtosService.uploadImagem()` usa:
    POST /api/produtos/{id}/imagem
    Content-Type: multipart/form-data
    Field name: "imagem"

  Este endpoint NÃO consta no ALINHAMENTO_FRONTEND.txt.

  PERGUNTAS:
    a) Este endpoint existe e está activo?
    b) Qual a estrutura de resposta? O frontend espera:
       `{ success: true, data: "https://..." }` com a URL directa da imagem
    c) Qual o tamanho máximo aceite? O frontend assume 5 MB.
    d) Formatos aceites? O frontend assume JPG, PNG, WebP.
    e) O MinIO/S3 devolve URL pública ou requer token para aceder à imagem?


________________________________________________________________________________
SECÇÃO 7 — CONFIGURAÇÕES FINANCEIRAS
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 7.1 — Resposta das operações PUT retorna a config           │
  │    actualizada ou apenas { success, message, data: null }?              │
  └─────────────────────────────────────────────────────────────────────────┘

  O ALINHAMENTO §2 descreve a resposta do PUT /valor-minimo como:
    { "success": true, "message": "...", "data": null }

  O composable `useConfiguracaoFinanceira.js` chama `carregarConfiguracao()`
  após cada PUT para refrescar o estado (GET separado).

  PERGUNTA: Para ativarPosPago, desativarPosPago, alterarLimitePosPago e
  alterarValorMinimo — a resposta é sempre `data: null`?
  Ou algum deles retorna a ConfiguracaoFinanceiraSistema actualizada?
  (se retornar, podemos eliminar o GET extra e melhorar a performance)


________________________________________________________________________________
SECÇÃO 8 — UNIDADES DE CONSUMO
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 8.1 — GET /api/unidades-consumo/minhas está implementado?  │
  └─────────────────────────────────────────────────────────────────────────┘

  O composable `useUnidadeConsumo.js` chama:
    GET /api/unidades-consumo/minhas

  E faz fallback para `GET /api/unidades-consumo` se a resposta for 400.
  O comentário no serviço diz "NOVO ENDPOINT (conforme REFATORACAO_HIERARQUIA_UNIDADES.txt)".

  PERGUNTAS:
    a) Este endpoint está implementado e activo?
    b) O filtro por role (ADMIN=global, GERENTE/ATENDENTE=sua unidade) funciona?
    c) Qual o parâmetro de status? A URL `GET /api/unidades-consumo/status/{status}`
       ainda existe ou foi substituída por query param?

  ─────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🔵 PERGUNTA 8.2 — Quais os valores válidos do enum `TipoUnidade`?      │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend usa os seguintes valores no seletor de tipo ao criar uma unidade:
    MESA_FISICA, QUARTO, AREA_EVENTO, ESPACO_LOUNGE, VIRTUAL

  PERGUNTA: Estes são todos os valores aceites pelo backend?
  Há algum obsoleto ou faltando? Em particular `VIRTUAL` é usado para delivery?


________________________________________________________________________________
SECÇÃO 9 — WEBSOCKET / TEMPO REAL
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 9.1 — WebSocket: qual o endpoint e protocolo?              │
  └─────────────────────────────────────────────────────────────────────────┘

  O repositório contém `docs/DOCUMENTACAO_WEBSOCKET_FRONTEND.md`, indicando
  que o WebSocket está documentado mas NÃO está integrado no código actual
  (nenhuma chamada `new WebSocket(...)` ou biblioteca STOMP encontrada).

  PERGUNTAS:
    a) O WebSocket é obrigatório para o funcionamento do sistema
       (ex: atualizações em tempo real de pedidos na cozinha)?
    b) Qual o endpoint WS? Ex: `ws://localhost:8080/ws` ?
    c) Qual a biblioteca/protocolo? STOMP sobre SockJS?
    d) Quais os tópicos/destinos? Ex: `/topic/pedidos/{unidadeAtendimentoId}`
    e) Sem WebSocket, como devemos fazer polling de pedidos activos?
       Qual o endpoint e intervalo recomendado?


________________________________________________________________________________
SECÇÃO 10 — ROLES / PERMISSÕES
________________________________________________________________________________

  ┌─────────────────────────────────────────────────────────────────────────┐
  │ 🟡 PERGUNTA 10.1 — Quais roles existem e qual o campo exacto?          │
  └─────────────────────────────────────────────────────────────────────────┘

  O frontend verifica roles em dois lugares com lógica diferente:

    auth.js (Pinia store) → `isAdmin`:
      user.value?.roles?.includes('ROLE_ADMIN') || user.value?.role === 'ADMIN'

    store login response → mapeia:
      roles: [`ROLE_${userData.tipoUsuario}`]
      role:  userData.tipoUsuario

  E o ALINHAMENTO menciona: ATENDENTE, GERENTE, ADMIN

  PERGUNTAS:
    a) Quais os roles exactos suportados pelo sistema?
       ADMIN, GERENTE, ATENDENTE — são todos? Existe COZINHEIRO, SUPERVISOR, etc.?
    b) No JWT, as roles vêm como `["ROLE_ADMIN"]` (com prefixo ROLE_) ou
       como `["ADMIN"]` (sem prefixo)?
    c) No response do login, o campo é `tipoUsuario` ou `role` ou `roles`?
    d) Um utilizador pode ter múltiplas roles?


________________________________________________________________________________
RESUMO — PRIORIZAÇÃO PARA PRÓXIMA REUNIÃO
________________________________________________________________________________

  🔴 BLOQUEANTE (impede funcionalidade core):
    1.1  Endpoint de login correcto
    1.2  Claims JWT correctos
    2.1  PRE_PAGO em modo anónimo (fundo via QR code)
    2.2  Listagem de fundos
    3.1  Finalizar vs Fechar pedido

  🟡 IMPORTANTE (funcionalidade degradada sem resposta):
    1.3  Refresh token / auth/me
    2.3  /fundos/config/valor-minimo vs valorMinimoOperacao
    3.2  Cancelar pedido: POST vs PUT, body vs query param
    3.3  Campo `origem` no pedido
    4.1  Endpoints de auditoria
    4.2  Módulos e acções de auditoria
    5.1  Endpoints de dashboard
    5.2  Enum StatusPedido completo
    6.1  Upload de imagem de produto
    9.1  WebSocket
    10.1 Roles exactas

  🔵 CLARIFICAÇÃO (código funciona mas pode ser incorrecto):
    2.4  Gateway AppyPay — método de teste
    3.4  POST /api/pagamentos
    3.5  fundoConsumoId no PedidoResponse
    7.1  PUT retorna config actualizada?
    8.1  /minhas e filtros de status
    8.2  Enum TipoUnidade completo

________________________________________________________________________________
BUGS CORRIGIDOS PELO FRONTEND (sem necessidade de resposta)
________________________________________________________________________________

  Durante esta revisão foram corrigidos 4 bugs no ModalNovoPedido.vue:

  1. `carregandoSaldoAberto` (ref undefined) — removido do template
  2. Alerta "Cliente não vinculado" aparecia em unidades anónimas — corrigido
     para `v-if="!unidade?.modoAnonimo && !unidade?.cliente"`
  3. `podeUsarPrePago` retornava false para unidades anónimas — corrigido:
     modo anónimo SEMPRE pode usar PRE_PAGO (backend valida via QR)
  4. `podeCriarPedido` e `criarPedido()` bloqueavam unidades anónimas em
     PRE_PAGO — corrigido: skip das verificações locais de fundo/saldo
     para modo anónimo (backend retorna 422 se saldo insuficiente)

________________________________________________________________________________
COMO RESPONDER
________________________________________________________________________________

  Por favor responder a este documento criando ou actualizando:

    RESPOSTAS_BACKEND_PERGUNTAS.md

  com as respostas numeradas (1.1, 1.2, ..., 10.1).
  Para cada endpoint confirmado, indicar também:
    - Método HTTP
    - Path completo (com /api prefix)
    - Body/params aceites
    - Estrutura de resposta de sucesso
    - Erros possíveis (status code + message)

================================================================================
  FIM DO DOCUMENTO
  Equipe Frontend — 01/03/2026
================================================================================
