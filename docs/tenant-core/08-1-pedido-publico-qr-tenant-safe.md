# Prompt 8.1 — Pedido público por QR (tenant-safe, sem pagamento)

Data: 2026-05-16

## Objetivo

Permitir criação de **Pedido** no fluxo público por QR operacional, garantindo:

- resolução QR → Tenant/Instituição/Unidade/Mesa
- validação de tenant ativo
- validação de produtos **pertencentes ao tenant do QR**
- criação de SessaoConsumo mínima (quando necessário)
- criação de Pedido/SubPedidos/Itens **sem pagamento** nesta fase

## O que esta fase NÃO faz

- não cria Pagamento
- não chama AppyPay
- não debita FundoConsumo
- não implementa callback tenant-aware
- não remove `getInstituicaoAtiva()` / QR legado

## Decisão: `tenant_id` em entidades transacionais

Nesta fase foi introduzido `tenant_id NOT NULL` em:

- `pedidos`
- `sub_pedidos`
- `itens_pedido`

Justificativa:

- pedido é entidade transacional de alta criticidade
- pedido público por QR não pode depender apenas de herança indireta via sessão/instituição
- facilita scoping futuro de queries/reports e reduz risco de cross-tenant

Backfill:

- pedidos: SessaoConsumo → Instituicao → Tenant (com fallbacks por mesa/unidade e fallback final para Tenant `LEGACY`)
- sub_pedidos: via pedido
- itens_pedido: via pedido/subpedido/produto

## Endpoints públicos

Base: `/api/public/q/{token}`

- `POST /api/public/q/{token}/pedidos`
  - cria pedido tenant-safe por token público (não requer JWT)
  - não usa TenantContext obrigatório; tenant é resolvido pelo token

## Validações (segurança)

- QR token deve existir, estar ativo e não revogado (404 em caso contrário — falha fechada)
- tenant do QR deve estar `ATIVO` (404 em caso contrário — falha fechada)
- produtos:
  - devem existir **no tenant do QR** (`findByIdAndTenantId`)
  - devem estar `ativo=true` e `disponivel=true`
  - qualquer falha retorna erro genérico (“Produto inválido ou indisponível.”) sem revelar cross-tenant

## Sessão mínima (SessaoConsumo)

Estratégia 8.1:

- para QR tipo `MESA`:
  - tenta reutilizar sessão `ABERTA` da mesa
  - caso não exista, cria sessão nova
- para outros tipos:
  - cria sessão nova por pedido

A sessão criada:

- pertence à Instituicao/Unidade/Mesa do QR
- é `modoAnonimo=true`
- `status=ABERTA`
- `tipoSessao=POS_PAGO` (não exige recarga prévia nesta fase)
- cria automaticamente `FundoConsumo` com saldo zero (invariante do domínio)

## Status inicial do pedido

- operacional: `CRIADO`
- financeiro: `NAO_PAGO`
- tipoPagamento: `POS_PAGO`

## Migração

- `V7__pedido_tenant_aware_public_qr.sql`

## Testes

Integração Postgres/Testcontainers:

- cria pedido por QR para tenant A (OK)
- bloqueia produto de tenant B usando QR do tenant A (400 genérico)
- token inválido não cria pedido (404)

Arquivo:

- `src/test/java/com/restaurante/qr/PublicQrPedidoIT.java`

## Próximos passos recomendados (Prompt 8.2)

- Idempotência (`Idempotency-Key` / chave persistida)
- Criação de pedido por QR com sessão reutilizável mais rica (mesa/restaurante)
- Hardening de estado/limites (tenant suspenso, limites por plano)
- Pagamento tenant-aware (fase separada)

