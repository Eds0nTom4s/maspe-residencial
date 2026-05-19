package com.restaurante.service.operacao;

import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TurnoOperacionalPolicy {

    public void assertCanOpen(TenantContext ctx) {
        assertHasAny(ctx, Set.of(
                TenantUserRole.TENANT_OWNER.name(),
                TenantUserRole.TENANT_ADMIN.name(),
                TenantUserRole.TENANT_OPERATOR.name(),
                TenantUserRole.TENANT_CASHIER.name()
        ));
    }

    public void assertCanClose(TenantContext ctx) {
        assertCanOpen(ctx);
    }

    public void assertCanForceClose(TenantContext ctx) {
        assertHasAny(ctx, Set.of(
                TenantUserRole.TENANT_OWNER.name(),
                TenantUserRole.TENANT_ADMIN.name()
        ));
    }

    public void assertCanCancel(TenantContext ctx) {
        assertCanForceClose(ctx);
    }

    public void assertCanRead(TenantContext ctx) {
        assertHasAny(ctx, Set.of(
                TenantUserRole.TENANT_OWNER.name(),
                TenantUserRole.TENANT_ADMIN.name(),
                TenantUserRole.TENANT_OPERATOR.name(),
                TenantUserRole.TENANT_FINANCE.name(),
                TenantUserRole.TENANT_CASHIER.name(),
                TenantUserRole.TENANT_KITCHEN.name()
        ));
    }

    private void assertHasAny(TenantContext ctx, Set<String> allowed) {
        if (ctx == null || ctx.platformAdmin()) {
            return;
        }
        Set<String> roles = ctx.roles() != null ? ctx.roles() : Set.of();
        for (String role : roles) {
            if (allowed.contains(role)) return;
        }
        throw new AccessDeniedException("Usuário não possui permissão para executar esta ação.");
    }
}

