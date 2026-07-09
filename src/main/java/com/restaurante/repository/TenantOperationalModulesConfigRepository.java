package com.restaurante.repository;

import com.restaurante.model.entity.TenantOperationalModulesConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantOperationalModulesConfigRepository extends JpaRepository<TenantOperationalModulesConfig, Long> {

    Optional<TenantOperationalModulesConfig> findByTenantId(Long tenantId);
}
