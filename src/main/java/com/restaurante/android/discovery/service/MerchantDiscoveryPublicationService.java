package com.restaurante.android.discovery.service;

import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal persisted opt-in mechanism. No administrative HTTP surface is introduced here. */
@Service
public class MerchantDiscoveryPublicationService {

    private final TenantRepository tenants;

    public MerchantDiscoveryPublicationService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public void setPublished(long tenantId, boolean published) {
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found."));
        tenant.setDiscoveryPublished(published);
        tenants.saveAndFlush(tenant);
    }
}
