# Prompt 15A — Claims de Tenant no JWT (Token Tenant-Scoped)

## Objetivo
Reduzir custo de resolução de tenant/roles por request no backend CONSUMA, mantendo segurança multi-tenant e fallback seguro via banco.

Entregas desta fase:
- Endpoint para **seleção explícita de tenant** pelo usuário autenticado.
- Emissão de **JWT tenant-scoped** com claims suficientes para montar `TenantContext`.
- `TenantResolver` com **fast-path** por claims (evita carregar lista/roles por request).
- Mantém compatibilidade com fluxo legado (`X-Tenant-Id` + consultas no banco).

## Conceitos

### Token GLOBAL vs TENANT
- **GLOBAL**: emitido no login; não carrega tenant selecionado.
- **TENANT**: emitido ao selecionar tenant; carrega `tenantId`, `tenantCode` e `tenantRoles`.

O escopo do token é indicado por claim `tokenType`:
- `GLOBAL`
- `TENANT`

### Claims do token tenant-scoped (TENANT)
Exemplo (conceitual):

```json
{
  "sub": "user@email.com",
  "userId": 123,
  "tokenType": "TENANT",
  "tenantId": 10,
  "tenantCode": "ROSA",
  "tenantRoles": ["TENANT_OWNER"],
  "tenantUserStatus": "ATIVO",
  "platformAdmin": false,
  "iat": 1710000000,
  "exp": 1710003600
}
```

## Endpoint: seleção explícita de tenant

`POST /api/auth/tenant/select` (em ambiente com `server.servlet.context-path=/api`; internamente o mapping é `/auth/tenant/select`)

Regras:
- Requer autenticação (token GLOBAL).
- Valida:
  - tenant existe e está `ATIVO`
  - usuário possui `TenantUser` `ATIVO` no tenant
- Emite token tenant-scoped (`tokenType` claim = `TENANT`).

## Resolução de tenant por request (TenantResolver)

### Fast-path: token TENANT
Quando o request possui `Authorization: Bearer <jwt>` e `tokenType=TENANT`:
- Lê claims `tenantId`, `tenantCode`, `tenantRoles`, `userId`.
- **Defesa em profundidade (ainda com DB):**
  - valida tenant `ATIVO`
  - valida membership `ATIVO` (revogação pós-emissão do token continua segura)
- Injeta roles `TENANT_*` no `TenantContext.roles`.

### Fallback: fluxo legado
Se não houver token TENANT, ou claims inválidas:
- continua suportado:
  - resolução por `X-Tenant-Id` quando necessário
  - resolução por membership único
  - enforcement por `TenantGuard` com fallback no banco

## Segurança
- Roles `TENANT_*` são **específicas do tenant atual**.
- Mesmo com token TENANT, há validação de:
  - tenant ativo
  - membership ativo
- Role insuficiente continua retornando `403` (code `TENANT_ROLE_FORBIDDEN`).
- Cross-tenant em recursos por id continua `404` (não revela existência).

## Testes
Adicionados testes de integração com filtros de segurança habilitados:
- seleção de tenant requer autenticação
- token TENANT contém claims esperadas
- token TENANT permite acesso a `/api/tenant/**` sem `X-Tenant-Id`
- seleção de tenant sem membership retorna `403`

## Dívidas / próximos passos (fora do Prompt 15A)
- `JwtAuthenticationFilter` ainda chama `CustomUserDetailsService.loadUserByUsername(...)` por request (consulta DB).
  - Otimização futura pode envolver:
    - cache curto de `UserDetails` por `sub`
    - JWT com authorities globais completas para evitar lookup do user (avaliar risco de revogação)
    - refresh token + blacklist, se necessário

