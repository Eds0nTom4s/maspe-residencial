package com.restaurante.repository;

import com.restaurante.model.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    List<TenantUser> findByTenantId(Long tenantId);

    List<TenantUser> findByUserId(Long userId);

    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);
}

