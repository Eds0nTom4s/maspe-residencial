package com.restaurante.repository;

import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.enums.TenantUserAccessOrigin;
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

    List<TenantUser> findAllByTenantIdAndUserId(Long tenantId, Long userId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    // Tenant-aware membership checks (Prompt 4)
    List<TenantUser> findByUserIdAndEstado(Long userId, TenantUserEstado estado);

    boolean existsByTenantIdAndUserIdAndEstado(Long tenantId, Long userId, TenantUserEstado estado);

    List<TenantUser> findAllByTenantIdAndUserIdAndEstado(Long tenantId, Long userId, TenantUserEstado estado);

    List<TenantUser> findAllByTenantIdAndRoleAndAccessOrigin(
            Long tenantId,
            TenantUserRole role,
            TenantUserAccessOrigin accessOrigin
    );

    Optional<TenantUser> findByTenantIdAndUserIdAndRoleAndAccessOrigin(
            Long tenantId,
            Long userId,
            TenantUserRole role,
            TenantUserAccessOrigin accessOrigin
    );

    boolean existsByTenantIdAndUserIdAndRoleAndEstado(Long tenantId, Long userId, TenantUserRole role, TenantUserEstado estado);

    boolean existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
            Long tenantId,
            Long userId,
            TenantUserRole role,
            TenantUserEstado estado,
            TenantUserAccessOrigin accessOrigin
    );

    long countByTenantIdAndEstado(Long tenantId, TenantUserEstado estado);

    long countByTenantIdAndRoleAndEstado(Long tenantId, TenantUserRole role, TenantUserEstado estado);

    long countByTenantIdAndRoleAndEstadoAndAccessOrigin(
            Long tenantId,
            TenantUserRole role,
            TenantUserEstado estado,
            TenantUserAccessOrigin accessOrigin
    );

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

    /**
     * Busca todos os vínculos ativos de um usuário com seus tenants ativos,
     * fazendo fetch join no Tenant para evitar N+1 e LazyInitializationException.
     * Garante que somente tenants com estado ATIVO sejam retornados.
     */
    @Query("""
            select tu from TenantUser tu
            join fetch tu.tenant t
            left join fetch t.businessAccount ba
            where tu.user.id = :userId
              and tu.estado = :memberEstado
              and t.estado = :tenantEstado
            order by t.id asc, tu.role asc, tu.id asc
            """)
    List<TenantUser> findActiveTenantOptionsByUserId(
            @Param("userId") Long userId,
            @Param("memberEstado") TenantUserEstado memberEstado,
            @Param("tenantEstado") com.restaurante.model.enums.TenantEstado tenantEstado
    );
}
