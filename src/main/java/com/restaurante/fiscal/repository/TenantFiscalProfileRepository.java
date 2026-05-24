package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.TenantFiscalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantFiscalProfileRepository extends JpaRepository<TenantFiscalProfile, Long> {
    Optional<TenantFiscalProfile> findByTenantId(Long tenantId);
}

