package com.restaurante.repository;

import com.restaurante.model.entity.TenantCardapioConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantCardapioConfigRepository extends JpaRepository<TenantCardapioConfig, Long> {

    Optional<TenantCardapioConfig> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndCardapioPublicadoTrue(Long tenantId);
}
