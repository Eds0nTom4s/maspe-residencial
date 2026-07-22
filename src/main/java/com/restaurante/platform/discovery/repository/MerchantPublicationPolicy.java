package com.restaurante.platform.discovery.repository;

import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.TenantEstado;
import org.springframework.stereotype.Component;

/**
 * Canonical public visibility rule for the transitional persisted Discovery read model.
 *
 * <p>The current schema has no merchant publication flag. A published public menu is therefore
 * the explicit publication signal, and an active institution/unit pair proves that the tenant has
 * a valid operational location. A merchant with a published but empty menu remains visible with
 * {@code catalogAvailable=false}; an unpublished menu remains hidden to avoid exposing private
 * tenants. Decoupling merchant publication requires a future explicit {@code discoveryPublished}
 * source of truth and is deliberately not inferred here.
 */
@Component
public final class MerchantPublicationPolicy {

    public TenantEstado requiredTenantState() {
        return TenantEstado.ATIVO;
    }

    public BusinessAccountEstado requiredAccountState() {
        return BusinessAccountEstado.ATIVA;
    }

    public boolean requiresPublishedCatalog() {
        return true;
    }

    public boolean requiresActiveOperationalLocation() {
        return true;
    }
}
