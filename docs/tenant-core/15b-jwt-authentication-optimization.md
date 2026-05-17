# Prompt 15B — Otimização da Autenticação JWT (sem `loadUserByUsername` por request)

## Objetivo
Reduzir latência e custo por request removendo o lookup obrigatório de `UserDetailsService.loadUserByUsername(...)` em toda requisição autenticada, **sem comprometer**:
- segurança operacional (usuário desativado)
- compatibilidade com tokens legados
- fluxo existente de login / tenant select / RBAC / TenantResolver

## Estratégia

### 1) Principal leve (`JwtPrincipal`)
Novo principal derivado apenas do JWT, sem password e sem acesso a banco:
- `userId`, `username`
- `tokenType` (`GLOBAL` | `TENANT`)
- `tenantId`, `tenantCode`, `tenantRoles` (quando token TENANT)
- `authorities` (roles globais + tenant roles)

### 2) Factory para Authentication (`JwtAuthenticationFactory`)
Constrói `Authentication` diretamente a partir de `Claims`:
- `roles` (CSV) → authorities globais (`ROLE_*`)
- `tenantRoles` (list) → authorities tenant (`TENANT_*`)

### 3) Filtro JWT otimizado (`JwtAuthenticationFilter`)
Novo comportamento:
1. valida assinatura/expiração do JWT
2. se token tem claims modernas (userId + roles):
   - monta `Authentication` via `JwtAuthenticationFactory`
   - **não chama** `loadUserByUsername`
3. se token não tem claims modernas:
   - usa fallback legado (`loadUserByUsername`) quando habilitado

### 4) Strict mode e validação opcional de usuário ativo
Properties (todas com default seguro para performance):
- `consuma.security.jwt.strict-user-validation=false`
- `consuma.security.jwt.validate-user-active=false`
- `consuma.security.jwt.allow-legacy-userdetails-fallback=true`

Validação leve (sem carregar `UserDetails`):
- `JwtUserStatusValidator` usa `UserRepository.existsByIdAndAtivoTrue(userId)`

**Nota:** Tenant ATIVO + membership ATIVO continuam sendo responsabilidade do `TenantResolver/TenantGuard` (Prompt 15A), evitando duplicação.

## Compatibilidade
- Tokens legados (sem `userId`) continuam funcionando via fallback, enquanto `allow-legacy-userdetails-fallback=true`.
- Token TENANT continua sendo resolvido por claims no `TenantResolver` e validado via banco (tenant/membership ATIVO).

## Testes
Adicionados testes de integração com filtros habilitados provando:
- token moderno autentica sem chamar `loadUserByUsername`
- token legado usa fallback e chama `loadUserByUsername`

