package com.restaurante.repository;

import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    List<TenantUser> findByTenantId(Long tenantId);

    List<TenantUser> findByUserId(Long userId);

    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);

    List<TenantUser> findAllByTenantIdAndUserId(Long tenantId, Long userId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    // Tenant-aware membership checks (Prompt 4)
    List<TenantUser> findByUserIdAndEstado(Long userId, TenantUserEstado estado);

    Optional<TenantUser> findByTenantIdAndUserIdAndEstado(Long tenantId, Long userId, TenantUserEstado estado);

    boolean existsByTenantIdAndUserIdAndEstado(Long tenantId, Long userId, TenantUserEstado estado);

    List<TenantUser> findAllByTenantIdAndUserIdAndEstado(Long tenantId, Long userId, TenantUserEstado estado);

    boolean existsByTenantIdAndUserIdAndRoleAndEstado(Long tenantId, Long userId, TenantUserRole role, TenantUserEstado estado);

    long countByTenantIdAndEstado(Long tenantId, TenantUserEstado estado);

    long countByTenantIdAndRoleAndEstado(Long tenantId, TenantUserRole role, TenantUserEstado estado);

    @Query("""
            select count(distinct tu.user.id)
            from TenantUser tu
            where tu.tenant.id = :tenantId
              and tu.estado <> :excluded
            """)
    long countDistinctUsersByTenantIdAndEstadoNot(@Param("tenantId") Long tenantId, @Param("excluded") TenantUserEstado excluded);

    @Query("""
            select tu from TenantUser tu
            join fetch tu.user u
            where tu.tenant.id = :tenantId
            """)
    List<TenantUser> findByTenantIdWithUser(@Param("tenantId") Long tenantId);
}
