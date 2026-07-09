package com.restaurante.repository;

import com.restaurante.model.entity.TenantUserProductionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantUserProductionScopeRepository extends JpaRepository<TenantUserProductionScope, Long> {

    Optional<TenantUserProductionScope> findByTenantIdAndUserIdAndAtivoTrue(Long tenantId, Long userId);

    Optional<TenantUserProductionScope> findByTenantIdAndUserId(Long tenantId, Long userId);
}

