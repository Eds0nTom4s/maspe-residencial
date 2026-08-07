package com.restaurante.android.foundation.identity;

import com.restaurante.model.enums.TenantEstado;
import java.util.UUID;

/** Internal-only merchant identity. Never serialize this context on a public response. */
public record MerchantIdentityContext(
        UUID merchantPublicId,
        Long tenantId,
        Long businessAccountId,
        String merchantSlug,
        TenantEstado tenantState) {
}
