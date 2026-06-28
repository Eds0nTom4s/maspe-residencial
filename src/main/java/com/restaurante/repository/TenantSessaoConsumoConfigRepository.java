package com.restaurante.repository;

import com.restaurante.model.entity.TenantSessaoConsumoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSessaoConsumoConfigRepository extends JpaRepository<TenantSessaoConsumoConfig, Long> {

    Optional<TenantSessaoConsumoConfig> findByTenantId(Long tenantId);
}
