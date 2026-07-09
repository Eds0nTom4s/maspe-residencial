package com.restaurante.billing.service;

import com.restaurante.billing.repository.TenantBillingCollectionPolicyRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantBillingCollectionPolicyService {

    private final TenantRepository tenantRepository;
    private final TenantBillingCollectionPolicyRepository repository;

    @Transactional(readOnly = true)
    public TenantBillingCollectionPolicy get(Long tenantId) {
        return repository.findByTenantId(tenantId).orElse(null);
    }

    @Transactional
    public TenantBillingCollectionPolicy upsert(Long tenantId, java.util.function.Consumer<TenantBillingCollectionPolicy> patcher) {
        if (tenantId == null) throw new BusinessException("TENANT_BILLING_COLLECTION_POLICY_NOT_FOUND");
        TenantBillingCollectionPolicy policy = repository.findByTenantId(tenantId).orElse(null);
        if (policy == null) {
            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
            policy = new TenantBillingCollectionPolicy();
            policy.setTenant(tenant);
        }
        patcher.accept(policy);
        return repository.save(policy);
    }
}

