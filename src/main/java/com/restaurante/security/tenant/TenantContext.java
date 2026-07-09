package com.restaurante.security.tenant;

import java.util.Set;

/**
 * Contexto de tenant resolvido por request.
 *
 * Observação:
 * - Mantido simples e imutável para evitar vazamento de estado entre requests.
 */
public record TenantContext(
        Long tenantId,
        String tenantCode,
        Long userId,
        Set<String> roles,
        TenantResolutionSource source,
        boolean platformAdmin,
        boolean selectedByPlatformAdmin
) {
}

