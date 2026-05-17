package com.restaurante.service.tenantadmin;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.enums.TenantUserRole;

import java.util.Set;

public final class TenantUsuarioPolicy {

    private TenantUsuarioPolicy() {}

    public static boolean actorIsOwner(Set<TenantUserRole> actorRoles) {
        return actorRoles.contains(TenantUserRole.TENANT_OWNER);
    }

    public static boolean actorIsAdmin(Set<TenantUserRole> actorRoles) {
        return actorRoles.contains(TenantUserRole.TENANT_ADMIN);
    }

    public static void assertActorCanManageUsers(Set<TenantUserRole> actorRoles) {
        if (!actorIsOwner(actorRoles) && !actorIsAdmin(actorRoles)) {
            throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
        }
    }

    public static void assertAdminCannotAssignOwnerOrAdmin(Set<TenantUserRole> actorRoles, Set<TenantUserRole> rolesToAssign) {
        if (!actorIsAdmin(actorRoles) || actorIsOwner(actorRoles)) {
            return;
        }
        if (rolesToAssign.contains(TenantUserRole.TENANT_OWNER) || rolesToAssign.contains(TenantUserRole.TENANT_ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
        }
    }

    public static void assertRolesNotEmpty(Set<TenantUserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException("Roles são obrigatórias.");
        }
    }

    public static void assertAdminCannotModifyOwnerOrAdmin(Set<TenantUserRole> actorRoles, Set<TenantUserRole> targetRoles) {
        if (!actorIsAdmin(actorRoles) || actorIsOwner(actorRoles)) {
            return;
        }
        if (targetRoles.contains(TenantUserRole.TENANT_OWNER) || targetRoles.contains(TenantUserRole.TENANT_ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
        }
    }
}

