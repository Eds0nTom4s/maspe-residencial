package com.restaurante.security;

import java.util.List;

public record TenantStompSession(
        Long userId,
        Long tenantId,
        String tenantCode,
        String tokenType,
        boolean platformAdmin,
        List<String> tenantRoles
) {
}
