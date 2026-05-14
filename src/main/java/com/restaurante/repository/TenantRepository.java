package com.restaurante.repository;

import com.restaurante.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByTenantCode(String tenantCode);

    boolean existsBySlug(String slug);

    boolean existsByTenantCode(String tenantCode);
}

