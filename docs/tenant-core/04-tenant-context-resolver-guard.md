# Tenant Core — TenantContext / Resolver / Guard (Prompt 4)

Data: 2026-05-15

## Objetivo

Introduzir infraestrutura de **tenancy request-scoped** e validação contextual, sem ainda refatorar
o domínio operacional (Produto/Pedido/Pagamento/etc.).

Esta fase cria:
- `TenantContext` (imutável)
- `TenantContextHolder` (ThreadLocal com clear em finally)
- `TenantResolutionSource` (origem da resolução)
- `TenantResolver` (JWT + headers; seleção por PLATFORM_ADMIN)
- `TenantGuard` (API de validação contextual)
- `TenantContextFilter` (popula/limpa contexto por request)

## Resolução de tenant (implementado nesta fase)

### JWT (via Authentication)
- Usuário com 1 membership ATIVO (`TenantUser`) -> tenant resolvido automaticamente.
- Usuário com múltiplos memberships -> exige header `X-Tenant-Id` (ou `X-Tenant-Code`).
- Usuário sem principal persistido (`User`) -> não resolve tenant (mantém legado).

### PLATFORM_ADMIN (provisório)
- Nesta base, `ROLE_ADMIN` é tratado como PLATFORM_ADMIN.
- PLATFORM_ADMIN não resolve tenant automaticamente; exige seleção explícita via header.

### Headers
- `X-Tenant-Id`: seleciona tenant por id (Long)
- `X-Tenant-Code`: seleciona tenant por tenantCode

## Filosofia (não quebrar legado)

O filtro resolve e coloca no contexto quando possível, mas:
- não aplica enforcement global em todos os endpoints ainda
- endpoints legados continuam operando sem tenant

O enforcement será aplicado incrementalmente por módulos nas fases seguintes (TenantGuard em endpoints tenant-aware).

## Observações importantes

- Ainda não há tenantId no JWT; o resolver usa o banco (`TenantUserRepository`) para inferir o tenant.
- A resolução/validação lança erro apenas quando existe seleção explícita inválida (headers inválidos / sem membership).

## Próximo passo

Aplicar TenantGuard em endpoints tenant-aware novos, e depois começar a tornar repositórios/queries operacionais tenant-scoped.

