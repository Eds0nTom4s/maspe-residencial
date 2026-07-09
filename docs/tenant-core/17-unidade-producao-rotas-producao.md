# Prompt 17 — UnidadeProducao + Rotas de Produção (tenant-aware)

## Objetivo
Criar fundação operacional para cozinha/bar/pastelaria/balcão:
- representar estação de produção como entidade (`UnidadeProducao`)
- mapear `CategoriaProduto → UnidadeProducao` via rota (`RotaProducaoCategoria`)
- direcionar `SubPedido` para `UnidadeProducao`
- expor endpoints dedicados para `TENANT_KITCHEN` (produção)

## Modelo

### UnidadeProducao
Entidade `unidades_producao`:
- `tenant_id`, `instituicao_id` obrigatórios
- `unidade_atendimento_id` opcional
- `codigo` único por `(tenant, instituicao, codigo)`
- `tipo` (`UnidadeProducaoTipo`)

### RotaProducaoCategoria
Entidade `rotas_producao_categoria`:
- `tenant_id` obrigatório
- `categoria_produto_id` → `categoria_produtos`
- `unidade_producao_id` → `unidades_producao`
- índice parcial garantindo **uma rota ativa** por `(tenant, categoria)`

### SubPedido
`sub_pedidos` ganha `unidade_producao_id` (nullable por compatibilidade).

## Migração
`V14__unidade_producao_rotas_producao.sql`
- cria tabelas `unidades_producao` e `rotas_producao_categoria`
- adiciona `sub_pedidos.unidade_producao_id`
- backfill:
  - cria `Produção Geral` (`codigo=GERAL`) por `(tenant, instituicao)` observado em `sub_pedidos`
  - associa subpedidos antigos à unidade `GERAL`

## Provisionamento
`TenantProvisioningService` cria:
- unidade default `GERAL` por tenant+instituição
- rota default para categoria default (`slug=geral`) quando existir

## Roteamento no pedido público por QR
`PublicQrPedidoService` agora define `SubPedido.unidadeProducao`:
- resolve por categoria via `RotaProducaoService`
- se itens do mesmo subpedido gerarem unidades diferentes, usa fallback `GERAL`
- se não existir unidade `GERAL`, ela é criada sob demanda (`UnidadeProducaoService`)

## Endpoints (Tenant)
Base: `/api/tenant/producao`
- `GET /unidades` (OWNER/ADMIN/OPERATOR/KITCHEN)
- `GET /rotas` (OWNER/ADMIN)
- `POST /rotas` (OWNER/ADMIN)
- `GET /unidades/{id}/subpedidos` (OWNER/ADMIN/OPERATOR/KITCHEN)
- `GET /subpedidos/{id}` (OWNER/ADMIN/OPERATOR/KITCHEN)
- `PATCH /subpedidos/{id}/status` (OWNER/ADMIN/OPERATOR/KITCHEN; KITCHEN restrito a `EM_PREPARACAO` e `PRONTO`)

## RBAC
- `TENANT_KITCHEN` é habilitado apenas nos endpoints de produção.
- `TENANT_FINANCE` é bloqueado em produção.

## Próximos passos (fora do escopo)
- KDS/tempo real (WebSocket), impressão, métricas de preparo
- múltiplas rotas/prioridades por categoria
- roteamento por carga/unidade
- endpoints dedicados de cozinha (lista por status, SLA etc.)

