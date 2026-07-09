# Prompt 39 — Business Templates / Modelos Operacionais CONSUMA

## Objetivo

Entregar uma camada de **Business Templates / Modelos Operacionais** para que o utilizador não comece do zero.
O utilizador escolhe um template e a plataforma provisiona automaticamente a estrutura operacional mínima.

Visão:

> “A ideia é sua. A estrutura é CONSUMA.”

Nesta fase (v1), implementa-se:

- `CONSUMA_PONTO_V1`
- `CONSUMA_REST_V1`

## Conceito

Um **BusinessTemplate** é um pacote versionado de estrutura operacional que define:

- entidades a criar
- políticas/padrões iniciais
- permissões e perfis iniciais
- QRs/links operacionais
- categorias iniciais do catálogo
- estrutura física (mesas, produção, POS/KDS, turnos/checklists), quando aplicável

## Diferença entre Ponto vs Rest

### CONSUMA_PONTO_V1

Operação simples (microcomércio / vendas rápidas por QR/link):

- sem mesas
- sem produção
- sem POS/KDS por default
- sem disciplina obrigatória de turno por default
- QR/link público principal pronto para uso

### CONSUMA_REST_V1

Operação física com produção:

- categorias separadas (comidas/bebidas/sobremesas/promoções)
- mesas + QR por mesa (quando informado)
- produção (cozinha + geral, bar opcional)
- rotas de produção padrão
- POS e KDS em estado pendente (registrável)
- políticas padrão orientadas a turnos/checklists/snapshot

## Endpoints

Base path (ambos aceites):

- `/platform/templates`
- `/platform/business-templates`

### Preview / dry-run

`POST /platform/templates/{templateCode}/preview`

- valida inputs
- devolve recursos planeados, políticas e limites
- **não persiste dados**

### Provisionamento transacional

`POST /platform/templates/{templateCode}/provision`

- valida inputs + plano/limites
- cria recursos em **transação** (rollback em erro)
- guarda versionamento no tenant (`templateCode/templateVersion/...`)
- devolve resultado detalhado do que foi criado

## Inputs

### Campos comuns

- `planoCodigo` (opcional; default `PILOTO`)
- `tenant.nomeNegocio`
- `tenant.slug`
- `tenant.tenantCode` (opcional; pode ser gerado)
- `tenant.tipo`
- `owner.nome`
- `owner.email` ou `owner.telefone` (pelo menos 1)

### Ponto

- `ponto.entregaManual` (default `false`)
- `ponto.allowPickup` (default `true`)

### Rest

- `rest.temMesas`
- `rest.quantidadeMesas` (obrigatório quando `temMesas=true`)
- `rest.gerarQrPorMesa` (default `true`)
- `rest.temBarSeparado` (default `false`)
- `rest.exigeTurnoAberto` (default `true`)
- `rest.entrega` (`NONE|MANUAL|CONSUMA_NETWORK`)

## Recursos criados (resumo)

### CONSUMA_PONTO_V1

- Tenant + Subscrição (plano)
- Instituição
- Unidade de atendimento principal
- Categorias: `geral`, `destaques`, `promocoes`
- QR principal
- Owner (User) + vínculo `TENANT_OWNER`
- Políticas agregadas por tenant (`tenant_operacao_policies`)
- Delivery policy básica (pickup + manual opcional)
- Defaults de métodos de pagamento + inventory policy default

### CONSUMA_REST_V1

- Tenant + Subscrição (plano)
- Instituição
- Unidade de atendimento principal
- Categorias: `comidas`, `bebidas`, `sobremesas`, `promocoes`
- QR principal
- Mesas + QR por mesa (quando aplicável)
- Unidades de produção: `GERAL`, `COZINHA`, `BAR` (opcional)
- Rotas de produção padrão por categoria
- POS principal (PENDENTE) + KDS cozinha (PENDENTE)
- Checklists tenant-specific: abertura + fecho (com itens)
- Políticas agregadas por tenant (`tenant_operacao_policies`)
- Delivery policy opcional
- Defaults de métodos de pagamento + inventory policy default

## Políticas padrão (fase v1)

As políticas são persistidas em `tenant_operacao_policies` (visão agregada inicial).

PONTO:

- `requireOpenTurnoForOrders=false`
- `logisticsMode=NONE|TENANT_MANUAL`
- `allowPickup=true|false`
- `stockMode=SIMPLE`
- `productionEnabled=false`
- `posEnabled=false`
- `kdsEnabled=false`

REST:

- `requireOpenTurnoForOrders=true` (configurável)
- `logisticsMode=NONE|TENANT_MANUAL|CONSUMA_NETWORK`
- `allowTableQr=true` quando `temMesas=true`
- `productionEnabled=true`
- `posEnabled=true`
- `kdsEnabled=true`
- `stockMode=OPTIONAL`
- `snapshotFinanceiroEnabled=true`
- `preFechoEnabled=true`

## Segurança / RBAC

- Apenas **PLATFORM_ADMIN** pode usar preview/provision nesta fase.
- Slug e tenantCode são validados contra duplicação.
- Não se aceita `tenantId` externo no payload.

## Limitações (fase atual)

Não inclui:

- self-service público completo
- subscrição/pagamento
- marketplace
- templates futuros (Artes, Bilhetes, Sport, Fornecedores)
- delivery avançado/rede de fornecedores completa

## Próximos passos

- upgrades versionados por tenant (`templateCode/templateVersion`)
- enforcement runtime das políticas agregadas (ex.: disciplina de turno por tenant)
- templates adicionais (Artes/Serviços/Bilhetes/Sport/Fornecedores)

