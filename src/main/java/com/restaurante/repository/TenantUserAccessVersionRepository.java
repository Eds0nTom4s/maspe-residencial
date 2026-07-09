package com.restaurante.repository;

import com.restaurante.model.entity.TenantUserAccessVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantUserAccessVersionRepository extends JpaRepository<TenantUserAccessVersion, Long> {

    Optional<TenantUserAccessVersion> findByTenantIdAndUserId(Long tenantId, Long userId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    @Query("select t.accessVersion from TenantUserAccessVersion t where t.tenant.id = :tenantId and t.user.id = :userId")
    Optional<Integer> findAccessVersion(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Query("select t.permissionsUpdatedAt from TenantUserAccessVersion t where t.tenant.id = :tenantId and t.user.id = :userId")
    Optional<LocalDateTime> findPermissionsUpdatedAt(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}

