package com.restaurante.android.discovery.service;

import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import org.springframework.stereotype.Component;

/** Central public eligibility policy; operational-unit selection is deliberately absent. */
@Component
public final class MerchantDiscoveryPublicationPolicy {

    public TenantEstado requiredTenantState() {
        return TenantEstado.ATIVO;
    }

    public BusinessAccountEstado requiredBusinessAccountState() {
        return BusinessAccountEstado.ATIVA;
    }

    public SubscricaoEstado requiredSubscriptionStateForCanonicalAccount() {
        return SubscricaoEstado.ATIVA;
    }

    public boolean requiresExplicitOptIn() {
        return true;
    }

    public boolean requiresPublishedCatalog() {
        return true;
    }

    public boolean allowsLegacyTenantWithoutBusinessAccount() {
        return true;
    }
}
