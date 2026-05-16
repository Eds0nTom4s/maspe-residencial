# Prompt 7 — QR público + cardápio tenant-safe

Data: 2026-05-15

## Objetivo

Introduzir um QR operacional seguro (token não enumerável e revogável) para permitir:

- resolver publicamente **Tenant/Instituição/Unidade/Mesa** por token
- carregar o **cardápio público** (categorias + produtos) **apenas do tenant do QR**

Sem implementar ainda:

- criação de pedido por QR
- sessão de consumo por QR
- pagamento por QR
- callbacks tenant-aware
- substituição do `QrCodeToken` legado

## Por que isso é necessário

O fluxo anterior de QR/cardápio era um risco crítico porque:

- tokens previsíveis/enumeráveis permitem varredura
- controllers públicos podiam expor catálogo global ou sem escopo por tenant
- em SaaS multi-tenant isso resulta em **data leakage** (tenant A acessando catálogo do tenant B)

## Modelo introduzido

Entidade: `QrCodeOperacional`

- pertence a `Tenant` (obrigatório)
- aponta para `Instituicao` (obrigatório)
- pode apontar para `UnidadeAtendimento` e `Mesa` (opcionais)
- possui `token` público **aleatório**, **único** e **não enumerável**
- pode ser **revogado** (falha fechada)

Enum: `QrCodeOperacionalTipo`

## Segurança do token

- tokens são gerados com `SecureRandom` e alfabeto sem caracteres ambíguos
- formato: prefixo `q_` + corpo randômico
- QR inativo/revogado/tenant não-ATIVO → resposta **404** (`ResourceNotFoundException`)
  - decisão deliberada para **não vazar** se o token existia

## Endpoints públicos

Base: `/api/public/q/{token}`

- `GET /api/public/q/{token}`
  - retorna metadados públicos do QR (tenant/instituição/unidade/mesa quando aplicável)
- `GET /api/public/q/{token}/cardapio`
  - retorna categorias ativas do tenant + produtos ativos/disponíveis do tenant
  - não depende de `TenantContext` (o tenant é resolvido pelo token)

## Cardápio público (escopo)

O cardápio público é carregado via repositórios tenant-aware:

- `CategoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId)`
- `ProdutoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(tenantId)`

Isso garante que o catálogo público nunca seja global.

## Migrações

- `V6__create_qr_code_operacional.sql`
  - cria tabela `qr_codes_operacionais`
  - adiciona constraints (FKs + unique token) e índices
  - não altera QR legado

## Testes

Testcontainers (PostgreSQL) valida o fluxo real:

- QR do tenant A resolve tenant A
- QR do tenant A retorna somente produtos do tenant A
- token inválido → 404
- QR revogado → 404

Arquivos principais:

- `src/test/java/com/restaurante/qr/PublicQrCardapioIT.java`
- `src/test/java/com/restaurante/qr/QrTokenGenerationIT.java`

## O que fica para as próximas fases

- integrar `QR_TOKEN` no `TenantResolver` (infra já preparada em `TenantResolutionSource`)
- criar pedido por QR tenant-safe
- sessão de consumo tenant-aware
- pagamento tenant-aware
- migração gradual do QR legado para `QrCodeOperacional`

