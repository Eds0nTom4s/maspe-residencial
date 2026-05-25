package com.restaurante.inventory.service;

import com.restaurante.inventory.repository.TenantInventoryPolicyRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantInventoryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantInventoryPolicyService {

    private final TenantInventoryPolicyRepository repository;

    @Transactional
    public TenantInventoryPolicy getOrCreateDefault(Tenant tenant) {
        return repository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantInventoryPolicy policy = new TenantInventoryPolicy();
            policy.setTenant(tenant);
            return repository.save(policy);
        });
    }
}

