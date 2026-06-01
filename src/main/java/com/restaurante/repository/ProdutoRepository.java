package com.restaurante.repository;

import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.restaurante.repository.projection.SyncAggProjection;
import java.util.Collection;

/**
 * Repository para operações de banco de dados com Produto
 */
@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca produto por código
     */
    Optional<Produto> findByCodigo(String codigo);

    /**
     * Verifica se existe produto com o código informado
     */
    boolean existsByCodigo(String codigo);

    /**
     * Busca produtos ativos com paginação
     */
    Page<Produto> findByAtivoTrue(Pageable pageable);

    /**
     * Busca produtos disponíveis com paginação
     */
    Page<Produto> findByDisponivelTrueAndAtivoTrue(Pageable pageable);

    Page<Produto> findByTenantIdAndDisponivelTrueAndAtivoTrue(Long tenantId, Pageable pageable);

    /**
     * Busca produtos por categoria com paginação
     */
    Page<Produto> findByCategoriaAndDisponivelTrueAndAtivoTrue(CategoriaProdutoLegacy categoria, Pageable pageable);

    Page<Produto> findByTenantIdAndCategoriaAndDisponivelTrueAndAtivoTrue(Long tenantId, CategoriaProdutoLegacy categoria, Pageable pageable);

    /**
     * Busca produtos por nome (busca parcial) com paginação
     */
    Page<Produto> findByNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(String nome, Pageable pageable);

    Page<Produto> findByTenantIdAndNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(Long tenantId, String nome, Pageable pageable);

    // Tenant-aware (Prompt 5)
    Optional<Produto> findByIdAndTenantId(Long id, Long tenantId);

    List<Produto> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    Optional<Produto> findByCodigoAndTenantId(String codigo, Long tenantId);

    boolean existsByCodigoAndTenantId(String codigo, Long tenantId);

    List<Produto> findByTenantId(Long tenantId);

    Page<Produto> findByTenantId(Long tenantId, Pageable pageable);

    List<Produto> findByTenantIdAndAtivoTrue(Long tenantId);

    List<Produto> findByTenantIdAndDisponivelTrueAndAtivoTrue(Long tenantId);

    List<Produto> findByTenantIdAndAtivoTrueAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);

    List<Produto> findByTenantIdAndDisponivelTrueAndAtivoTrueAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);

    List<Produto> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);

    List<Produto> findByTenantIdAndCategoriaProdutoIdAndAtivoTrue(Long tenantId, Long categoriaProdutoId);

    Page<Produto> findByTenantIdAndCategoriaProdutoIdAndDisponivelTrueAndAtivoTrue(Long tenantId, Long categoriaProdutoId, Pageable pageable);

    @Query("SELECT p FROM Produto p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND (:includeInactive = true OR (p.ativo = true AND p.disponivel = true)) " +
           "AND (cast(:updatedSince as timestamp) IS NULL OR p.updatedAt > :updatedSince) " +
           "AND (cast(:lastId as string) IS NULL OR p.id > :lastId) " +
           "ORDER BY p.id ASC")
    List<Produto> syncKeyset(
            @Param("tenantId") Long tenantId,
            @Param("includeInactive") boolean includeInactive,
            @Param("updatedSince") LocalDateTime updatedSince,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    @Query("""
            select count(p) as count,
                   max(p.updatedAt) as maxUpdatedAt,
                   max(p.createdAt) as maxCreatedAt,
                   sum(case when p.updatedAt is null then 1 else 0 end) as nullUpdatedAtCount
            from Produto p
            where p.tenant.id = :tenantId
              and (:includeInactive = true or (p.ativo = true and p.disponivel = true))
            """)
    SyncAggProjection computeSyncAgg(@Param("tenantId") Long tenantId, @Param("includeInactive") boolean includeInactive);

    long countByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);
}
