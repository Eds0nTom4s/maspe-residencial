package com.restaurante.repository;

import com.restaurante.model.entity.RotaProducaoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.restaurante.repository.projection.SyncAggProjection;

@Repository
public interface RotaProducaoCategoriaRepository extends JpaRepository<RotaProducaoCategoria, Long> {

    Optional<RotaProducaoCategoria> findByTenantIdAndCategoriaProdutoIdAndAtivoTrue(Long tenantId, Long categoriaProdutoId);

    List<RotaProducaoCategoria> findByTenantId(Long tenantId);

    Optional<RotaProducaoCategoria> findByIdAndTenantId(Long id, Long tenantId);

    List<RotaProducaoCategoria> findByTenantIdAndAtivoTrue(Long tenantId);

    List<RotaProducaoCategoria> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);

    long countByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    @Query("""
            select count(r) as count,
                   max(r.updatedAt) as maxUpdatedAt,
                   max(r.createdAt) as maxCreatedAt,
                   sum(case when r.updatedAt is null then 1 else 0 end) as nullUpdatedAtCount
            from RotaProducaoCategoria r
            where r.tenant.id = :tenantId
              and r.ativo = true
            """)
    SyncAggProjection computeSyncAgg(@Param("tenantId") Long tenantId);
}
