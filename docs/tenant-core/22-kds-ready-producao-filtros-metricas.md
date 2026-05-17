# Prompt 22 — Produção KDS-ready (HTTP): filtros, paginação, “minha unidade” e métricas

## Objetivo
Preparar a operação de produção (cozinha/bar/pastelaria) para uso real em piloto **sem UI**, **sem WebSocket/SSE** e **sem impressão**, focando em endpoints HTTP tenant-aware e device-aware.

Entregas principais:
- Resolver automaticamente “minha unidade de produção” para usuários `TENANT_KITCHEN` e para dispositivos `DevicePrincipal`.
- Listagem paginada de subpedidos para KDS (minha unidade) e listagem geral (owner/admin/operator).
- Filtros robustos (status/período/busca) com proteção contra consultas amplas.
- Métricas básicas de tempos a partir de timestamps do `SubPedido`.

## Endpoints
Base: `/tenant/producao`

### 1) Minha unidade de produção
`GET /tenant/producao/minha-unidade`

Retorna a unidade de produção mais adequada para o ator atual.

Regras:
- **Usuário humano** (OWNER/ADMIN/OPERATOR/KITCHEN): se existir **apenas 1 unidade ativa** no tenant, retorna essa. Se houver múltiplas, retorna `modoResolucao=EXPLICIT_REQUIRED` com `opcoes`.
- **DevicePrincipal** com capability `VIEW_PRODUCTION`: resolve preferencialmente por `unidadeAtendimentoId` do device; se não encontrar, aplica fallback seguro (instituição/tenant) e pode retornar 409 quando ambíguo.

### 2) Subpedidos da minha unidade (KDS)
`GET /tenant/producao/minha-unidade/subpedidos`

Filtros:
- `status` (opcional)
- `de`, `ate` (opcional — ISO_DATE_TIME)
- `search` (opcional)
- `page`, `size` (Pageable)

Regras:
- Sempre tenant-scoped.
- Sempre paginado.
- Aplica lookback padrão quando `de/ate` não são informados.

### 3) Subpedidos geral tenant-scoped (admin/operator)
`GET /tenant/producao/subpedidos`

Permitido:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`

Filtros:
- `unidadeProducaoId` (opcional)
- `status` (opcional)
- `pedidoNumero` (opcional)
- `de`, `ate` (opcional)
- `page`, `size`

Regras:
- DevicePrincipal **não** usa este endpoint.
- `TENANT_KITCHEN` deve operar via `/minha-unidade/*`.

### 4) Métricas gerais de produção
`GET /tenant/producao/metricas`

Permitido:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`

Calcula métricas básicas no período (com lookback padrão quando `de/ate` não informados), ignorando timestamps ausentes (`null`) nas médias.

### 5) Métricas da minha unidade
`GET /tenant/producao/minha-unidade/metricas`

Permitido:
- `TENANT_KITCHEN`, `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`
- `DevicePrincipal` com `VIEW_PRODUCTION`

## Proteções (consulta/paginação)
Properties (defaults):
- `consuma.producao.default-lookback-hours=12`
- `consuma.producao.default-page-size=25`
- `consuma.producao.max-page-size=100`
- `consuma.producao.atrasado-threshold-minutes=30`

Regras:
- Se o cliente não informar `de/ate`, aplica lookback padrão.
- Se `size` exceder o máximo, o backend limita para `max-page-size`.
- Não existe listagem não paginada.

## Scope por usuário vs device
- **Usuário**: tenant vem de `TenantContext`.
- **Device**: tenant vem de `DevicePrincipal`.
- `DevicePrincipal` é aceito apenas onde explicitamente permitido (`minha-unidade/*`), e deve possuir `DeviceCapability.VIEW_PRODUCTION` para leitura.

## Métricas
MVP calcula médias a partir de:
- `createdAt` (BaseEntity)
- `iniciadoEm`
- `prontoEm`
- `entregueEm`

Médias:
- `tempoMedioAteIniciarSegundos = iniciadoEm - createdAt`
- `tempoMedioAteProntoSegundos = prontoEm - createdAt`
- `tempoMedioTotalPreparacaoSegundos = prontoEm - iniciadoEm`

Se o timestamp necessário estiver `null`, o subpedido é ignorado na média correspondente.

## Limitações (intencionais)
- Sem WebSocket/SSE.
- Sem UI.
- Sem impressão.
- Sem roteamento inteligente por carga.
- Métricas ainda não são agregadas por SQL avançado/particionamento; período é limitado por lookback.

## Próximos passos recomendados
- Seleção explícita de unidade para usuário `TENANT_KITCHEN` quando houver múltiplas unidades (persistir preferência).
- Endpoints read-only de sync para KDS/POS (catálogo/filas) e, depois, evolução para SSE/WebSocket.
- Métricas avançadas: percentis, SLA, atraso por unidade, agregação via SQL.

