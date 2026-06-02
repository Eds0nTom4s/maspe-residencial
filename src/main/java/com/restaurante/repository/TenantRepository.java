package com.restaurante.repository;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByTenantCode(String tenantCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySlug(String slug);

    boolean existsByTenantCode(String tenantCode);

    long countByEstado(TenantEstado estado);

    @Query("""
            select t
            from Tenant t
            where (cast(:estado as string) is null or t.estado = :estado)
              and (cast(:search as string) is null or :search = '' or lower(t.nome) like lower(concat('%', :search, '%')) or lower(t.tenantCode) like lower(concat('%', :search, '%')))
            order by t.id asc
            """)
    Page<Tenant> searchPlatform(@Param("estado") TenantEstado estado, @Param("search") String search, Pageable pageable);
}
