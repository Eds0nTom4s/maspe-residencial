package com.restaurante.repository;

import com.restaurante.model.entity.QrCodeOperacional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.restaurante.repository.projection.QrAggProjection;

public interface QrCodeOperacionalRepository extends JpaRepository<QrCodeOperacional, Long> {

    Optional<QrCodeOperacional> findByToken(String token);

    Optional<QrCodeOperacional> findByTokenAndAtivoTrueAndRevogadoFalse(String token);

    List<QrCodeOperacional> findByTenantId(Long tenantId);

    Optional<QrCodeOperacional> findByIdAndTenantId(Long id, Long tenantId);

    Optional<QrCodeOperacional> findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(Long mesaId, Long tenantId);

    List<QrCodeOperacional> findByTenantIdAndAtivoTrueAndRevogadoFalse(Long tenantId);

    List<QrCodeOperacional> findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalse(Long tenantId, Long unidadeAtendimentoId);

    List<QrCodeOperacional> findByTenantIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    List<QrCodeOperacional> findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(Long tenantId, Long unidadeAtendimentoId, LocalDateTime updatedSince);

    long countByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    long countByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(Long tenantId, Long unidadeAtendimentoId, LocalDateTime updatedSince);

    boolean existsByToken(String token);

    long countByTenantId(Long tenantId);

    @Query("""
            select count(q) as count,
                   max(q.updatedAt) as maxUpdatedAt,
                   max(q.createdAt) as maxCreatedAt,
                   max(q.revogadoEm) as maxRevogadoEm,
                   sum(case when q.updatedAt is null then 1 else 0 end) as nullUpdatedAtCount
            from QrCodeOperacional q
            where q.tenant.id = :tenantId
              and q.ativo = true
              and q.revogado = false
              and (cast(:unidadeAtendimentoId as string) is null or q.unidadeAtendimento.id = :unidadeAtendimentoId)
            """)
    QrAggProjection computeSyncAgg(@Param("tenantId") Long tenantId, @Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    @Query("""
            select count(q)
            from QrCodeOperacional q
            where q.tenant.id = :tenantId
              and q.ativo = true
              and q.revogado = false
              and (cast(:unidadeAtendimentoId as string) is null or q.unidadeAtendimento.id = :unidadeAtendimentoId)
            """)
    long countSyncByTenantAndScope(@Param("tenantId") Long tenantId, @Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    @Query("""
            select q
            from QrCodeOperacional q
            where q.tenant.id = :tenantId
              and q.ativo = true
              and q.revogado = false
              and (cast(:unidadeAtendimentoId as string) is null or q.unidadeAtendimento.id = :unidadeAtendimentoId)
              and (cast(:updatedSince as timestamp) is null or q.updatedAt > :updatedSince)
              and (cast(:lastId as string) is null or q.id > :lastId)
            order by q.id asc
            """)
    List<QrCodeOperacional> syncKeyset(@Param("tenantId") Long tenantId,
                                       @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
                                       @Param("updatedSince") LocalDateTime updatedSince,
                                       @Param("lastId") Long lastId,
                                       org.springframework.data.domain.Pageable pageable);
}
