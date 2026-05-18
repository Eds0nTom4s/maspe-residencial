package com.restaurante.repository;

import com.restaurante.model.entity.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.restaurante.repository.projection.SyncAggProjection;

public interface CategoriaProdutoRepository extends JpaRepository<CategoriaProduto, Long> {

    Optional<CategoriaProduto> findByIdAndTenantId(Long id, Long tenantId);

    Optional<CategoriaProduto> findBySlugAndTenantId(String slug, Long tenantId);

    boolean existsBySlugAndTenantId(String slug, Long tenantId);

    List<CategoriaProduto> findByTenantId(Long tenantId);

    List<CategoriaProduto> findByTenantIdAndAtivoTrueOrderByOrdemAsc(Long tenantId);

    List<CategoriaProduto> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);

    @Query("""
            select count(c) as count,
                   max(c.updatedAt) as maxUpdatedAt,
                   max(c.createdAt) as maxCreatedAt,
                   sum(case when c.updatedAt is null then 1 else 0 end) as nullUpdatedAtCount
            from CategoriaProduto c
            where c.tenant.id = :tenantId
              and (:includeInactive = true or c.ativo = true)
            """)
    SyncAggProjection computeSyncAgg(@Param("tenantId") Long tenantId, @Param("includeInactive") boolean includeInactive);

    long countByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);
}
