# ORDER_ORIGIN_POLICY_001 — Formalização da Origem do Pedido

## 1. Objetivo

Tornar a origem de um pedido um dado explícito, estável, auditável e usado pelas políticas operacionais do backend, eliminando inferências frágeis baseadas apenas em sessão, mesa ou template do tenant.

Esta fase (`order-origin-hardening-001`) NÃO implementa PDV real, KDS completo, gateway, estorno ou caixa por turno. Apenas formaliza o conceito de origem e o integra às policies já existentes.

## 2. Diferença entre template operacional e origem do pedido

| Conceito | O que representa | Exemplos |
|---|---|---|
| **Template operacional** | Modelo de operação do tenant / fluxo de negócio. | `CONSUMA_REST_V1`, `CONSUMA_PONTO_V1`, `PDV_INTERNO`, `KDS`, `CAIXA` |
| **Origem do pedido** | Canal concreto por onde o pedido nasceu no sistema. | `QR_MESA`, `QR_PRINCIPAL`, `DEVICE_POS`, `OPERADOR_INTERNO`, `SESSAO_CONSUMO` |

O template define **o que é possível** no negócio; a origem define **quem criou o pedido e em que contexto**. Ambos são insumos para as policies de aceite, pagamento e produção.

## 3. Enum / tipo de origem

Enum canonical backend:

```java
package com.restaurante.model.enums;

public enum PedidoOrigem {
    QR_MESA,
    QR_PRINCIPAL,
    QR_PUBLICO,
    SESSAO_PARTICIPANTE,
    SESSAO_CONSUMO,
    OPERADOR_INTERNO,
    DEVICE_POS,
    PDV_INTERNO,
    DEVICE_KDS,
    CAIXA,
    SISTEMA,
    LEGADO,
    UNKNOWN
}
```

Frontend reconhece o tipo `PedidoOrigemBackend` com os mesmos valores e os normaliza para labels discretas (`MESA`, `QR`, `PONTO`, `Operador`, etc.) apenas para exibição informativa.

## 4. Como a origem é definida em QR_MESA

Fluxo: `PublicQrPedidoService.criarPedidoPublicoPorQrToken`.

- O backend resolve o `QrCodeOperacional` a partir do token público.
- Se `qr.getTipo() == QrCodeOperacionalTipo.MESA`, define `pedidoOrigem = PedidoOrigem.QR_MESA`.
- O frontend nunca informa a origem; ela é derivada do QR validado no backend.

## 5. Como a origem é definida em QR_PRINCIPAL / PUBLIC_MENU

Mesmo fluxo de QR público:

- QR geral do tenant/instituição/unidade/balcão (tipo diferente de `MESA`) → `PedidoOrigem.QR_PRINCIPAL`.
- Fallback defensivo quando o tipo do QR é nulo → `PedidoOrigem.QR_PUBLICO`.

A distinção `QR_PRINCIPAL` vs `QR_PUBLICO` permite, no futuro, diferenciar QR raiz do tenant de QR genérico/public-menu quando necessário. Nesta fase ambos herdam a mesma regra de aceite obrigatório.

## 6. Como a origem é definida em PDV / DEVICE_POS

Fluxo: `DevicePedidoService.criarPedido`.

- Dispositivo autenticado com capability `CREATE_ORDER` cria o pedido com `pedidoOrigem = PedidoOrigem.DEVICE_POS`.
- `PDV_INTERNO` é reservado para pedidos criados explicitamente por um operador autenticado no fluxo PDV interno (ainda não implementado de ponta a ponta).

## 7. Como a origem é definida em OPERADOR_INTERNO / SESSAO_CONSUMO / SESSAO_PARTICIPANTE

Fluxo: `PedidoService.criar`.

- Chamada padrão do painel tenant → `OPERADOR_INTERNO`.
- Pedido atribuído a um participante (`request.getParticipanteId() != null`) → `SESSAO_PARTICIPANTE`.
- Pedido criado por cliente identificado via app/telefone → `SESSAO_CONSUMO`.
- Sessão anônima com mesa → `QR_MESA`; com QR de sessão → `QR_PRINCIPAL`; demais → `SESSAO_CONSUMO`.

## 8. Como a origem é tratada em legado

Migration: `V75__pedido_origem.sql`.

```sql
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS pedido_origem VARCHAR(30);
UPDATE pedidos SET pedido_origem = 'LEGADO' WHERE pedido_origem IS NULL;
```

- Coluna aditiva, nullable, sem `NOT NULL` para permitir rollback.
- Dados antigos recebem `LEGADO`, que é um fallback conservador.
- `OperationalTemplatePolicy.resolvePedidoOrigem` aplica a seguinte prioridade:
  1. Origem persistida no pedido (se não nula).
  2. Actor conhecido (`DEVICE_POS`, `DEVICE_KDS`, operador tenant, `SYSTEM`).
  3. Fallback por sessão/mesa/QR para dados legados sem origem explícita.
  4. `LEGADO` se não houver sessão; `UNKNOWN` se não for possível determinar.

## 9. Relação com OperationalTemplatePolicy

A policy passou a receber a origem resolvida como parâmetro explícito nos métodos:

- `requiresAcceptanceBeforePayment(String templateCode, PedidoOrigem origem)`
- `allowsImmediatePayment(String templateCode, PedidoOrigem origem, OperationalOrigem actor)`
- `productionFlow(String templateCode, PedidoOrigem origem)`
- `canAccept(OperationalOrigem actor, String templateCode, PedidoOrigem origem)`
- `canReject(...)`, `canConfirmPayment(...)`, `canMoveProduction(...)`

Regras centrais:

- `QR_MESA`, `QR_PRINCIPAL`, `QR_PUBLICO`, `SESSAO_PARTICIPANTE` exigem aceite antes do pagamento.
- `DEVICE_POS` e `PDV_INTERNO` podem permitir pagamento imediato, conforme actor/template.
- `DEVICE_KDS` não confirma pagamento, mas pode movimentar produção.
- `CAIXA` não envia para produção.

## 10. Relação com PedidoPagamentoPolicy

`PedidoPagamentoPolicy` consome `OperationalTemplatePolicy.requiresAcceptanceBeforePayment(...)` para bloquear pagamento de pedidos públicos ainda não aceites. A origem usada é a resolvida pela policy, não inferida localmente.

## 11. Relação com PedidoAllowedActionsService

`PedidoAllowedActionsService.evaluate(...)` resolve template + origem via `OperationalTemplatePolicy` e só então calcula `allowedActions` / `actionReasons`. O frontend continua usando `allowedActions` como única autoridade para exibir/habilitar ações; `pedidoOrigem` é apenas informativo.

## 12. Fallback conservador

| Situação | Comportamento |
|---|---|
| Pedido com origem persistida | Usa origem persistida |
| Pedido sem origem, actor é DEVICE_POS/KDS | Usa actor |
| Pedido sem origem, actor é operador tenant | `OPERADOR_INTERNO` |
| Pedido sem origem, com sessão + mesa | `QR_MESA` (fallback) |
| Pedido sem origem, com sessão + QR | `QR_PRINCIPAL` (fallback) |
| Pedido sem origem e sem sessão | `LEGADO` |
| Impossível determinar | `UNKNOWN` |

## 13. Auditoria / OperationalEventLog

- Novo evento `OperationalEventType.PEDIDO_CRIADO`.
- `OperationalEventLogService.logPedidoCriado(...)` registra `pedidoOrigem` no metadata.
- Eventos de aceite/rejeição/cancelamento/bloqueio já registram `tenantTemplate` e `pedidoOrigem`.
- Device: `PEDIDO_CRIADO_DEVICE` registra `pedidoOrigem` no metadata.

## 14. Contrato de resposta

Os DTOs de resposta expõem `pedidoOrigem` de forma aditiva:

- `PedidoResponse`
- `TenantPedidoDetalheResponse`
- `TenantPedidoResumoResponse`

O frontend normaliza para `origem` (label discreta), mas não usa o valor para liberar ações críticas.

## 15. Próximas lacunas

- `CONFIRM_PAYMENT` continua sem fluxo financeiro real no painel tenant (já documentado em fases anteriores).
- Produção ainda é movimentada por pedido, não por subpedido, nesta fase.
- `VIEW_EXTRACT` permanece reservado.
- PDV interno (`PDV_INTERNO`) e caixa (`CAIXA`) têm valores de origem mapeados, mas sem fluxos completos implementados.
- KDS completo não foi implementado; `DEVICE_KDS` já está preparado nas policies.
