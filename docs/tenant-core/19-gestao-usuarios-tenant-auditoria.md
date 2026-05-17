# Prompt 19 — Gestão mínima de usuários do tenant + auditoria operacional

## Objetivo
Permitir que `TENANT_OWNER`/`TENANT_ADMIN` gerenciem a equipe do tenant sem depender da equipe CONSUMA:
- listar usuários do tenant
- criar/convidar (vincular) usuário
- atribuir/alterar roles `TENANT_*`
- suspender/reativar/remover (remoção lógica) memberships
- aplicar `maxUsuarios`
- registrar auditoria operacional mínima

## Modelo: `User` vs `TenantUser`
- `User` é global (identidade/autenticação).
- `TenantUser` é o vínculo do `User` com um `Tenant` com:
  - `role` (uma linha por role)
  - `estado` (`ATIVO`, `PENDENTE`, `SUSPENSO`, `REMOVIDO`)
- A API expõe **roles agregadas por usuário** (mesmo que internamente seja 1 linha por role).

## RBAC e regras de política
Permissões:
- Apenas `TENANT_OWNER` e `TENANT_ADMIN` acessam `/api/tenant/usuarios/**` e `/api/tenant/auditoria`.

Restrições:
- `TENANT_ADMIN` não pode criar/atribuir `TENANT_OWNER` nem `TENANT_ADMIN`.
- `TENANT_ADMIN` não pode alterar/suspender/remover usuários que possuam `TENANT_OWNER` ou `TENANT_ADMIN`.
- `TENANT_OWNER` pode gerir todos, mas **não pode suspender/remover o último OWNER ATIVO** do tenant.

## Limite `maxUsuarios`
Aplicado via `TenantLimitService.assertCanCreateUser(tenantId, quantidadeNova)`.
Regra de contagem:
- conta **usuários distintos** com pelo menos um vínculo `TenantUser` com `estado != REMOVIDO`
- `SUSPENSO` consome limite; `REMOVIDO` não consome.

Em excedente:
- operação é bloqueada e retorna `409 Conflict` com mensagem segura.

## Auditoria operacional mínima
Tabela `tenant_audit_logs` registra ações administrativas:
- criação/vínculo de usuário
- alteração de roles
- suspensão/reativação/remoção
- bloqueios de limite

Não registra:
- senha temporária
- secrets

## Endpoints
Base: `/api/tenant/usuarios`
- `GET /api/tenant/usuarios`
- `GET /api/tenant/usuarios/{userId}`
- `POST /api/tenant/usuarios`
- `PUT /api/tenant/usuarios/{userId}/roles`
- `POST /api/tenant/usuarios/{userId}/suspender`
- `POST /api/tenant/usuarios/{userId}/reativar`
- `DELETE /api/tenant/usuarios/{userId}`

Auditoria:
- `GET /api/tenant/auditoria`

## Observação importante (JWT tenant-scoped)
Após alteração de roles, um token tenant-scoped antigo pode continuar carregando roles antigas até expirar.
Mitigação futura (fora do escopo desta fase):
- `tokenVersion` em `TenantUser`/`User` e invalidação lógica
- refresh token

