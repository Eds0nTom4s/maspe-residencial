package com.restaurante.repository;

import com.restaurante.model.entity.TenantLimiteOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantLimiteOverrideRepository extends JpaRepository<TenantLimiteOverride, Long> {

    Optional<TenantLimiteOverride> findByTenantIdAndAtivoTrue(Long tenantId);
}

