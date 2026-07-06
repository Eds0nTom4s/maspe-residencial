package com.restaurante.financeiro.repository;

import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdemPagamentoRepository extends JpaRepository<OrdemPagamento, Long> {

    Optional<OrdemPagamento> findByTokenQr(String tokenQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdemPagamento o where o.id = :id")
    Optional<OrdemPagamento> findForUpdateById(@Param("id") Long id);

    Optional<OrdemPagamento> findByIdAndTenantId(Long id, Long tenantId);

    Optional<OrdemPagamento> findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(Long tenantId, Long pedidoId);

    List<OrdemPagamento> findByTenantIdAndPedidoIdAndStatusOrderByCreatedAtDesc(Long tenantId,
                                                                                 Long pedidoId,
                                                                                 OrdemPagamentoStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from OrdemPagamento o
              join fetch o.pedido p
            where o.tenant.id = :tenantId
              and p.id = :pedidoId
              and o.tipo = :tipo
            order by o.createdAt desc
            """)
    List<OrdemPagamento> findPedidoOrdersForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("pedidoId") Long pedidoId,
                                                   @Param("tipo") OrdemPagamentoTipo tipo,
                                                   Pageable pageable);

    @Query("""
            select o
            from OrdemPagamento o
            where o.tenant.id = :tenantId
              and (:tipo is null or o.tipo = :tipo)
              and (:status is null or o.status = :status)
              and (:turnoId is null or o.turnoOperacional.id = :turnoId)
              and (:unidadeAtendimentoId is null or o.unidadeAtendimento.id = :unidadeAtendimentoId)
              and (:de is null or o.createdAt >= :de)
              and (:ate is null or o.createdAt <= :ate)
            order by o.createdAt desc
            """)
    Page<OrdemPagamento> searchByTenantAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("tipo") OrdemPagamentoTipo tipo,
            @Param("status") OrdemPagamentoStatus status,
            @Param("turnoId") Long turnoId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            Pageable pageable
    );

    @Query("""
            select o
            from OrdemPagamento o
              left join fetch o.confirmadoPorDispositivo d
            where o.tenant.id = :tenantId
              and o.turnoOperacional.id = :turnoId
            order by o.createdAt asc
            """)
    List<OrdemPagamento> findAllByTenantIdAndTurnoOperacionalId(@Param("tenantId") Long tenantId,
                                                                @Param("turnoId") Long turnoId);

    @Query("""
            select o
            from OrdemPagamento o
              left join fetch o.confirmadoPorDispositivo d
            where o.tenant.id = :tenantId
              and o.turnoOperacional.id = :turnoId
              and o.status = :status
            order by o.createdAt asc
            """)
    List<OrdemPagamento> findAllByTenantIdAndTurnoOperacionalIdAndStatus(@Param("tenantId") Long tenantId,
                                                                         @Param("turnoId") Long turnoId,
                                                                         @Param("status") OrdemPagamentoStatus status);

    @Query("""
            select o
            from OrdemPagamento o
              left join fetch o.confirmadoPorDispositivo d
            where o.tenant.id = :tenantId
              and o.caixaOperadorSession.id = :caixaId
              and o.status = :status
            order by o.confirmadoEm asc
            """)
    List<OrdemPagamento> findAllByTenantIdAndCaixaOperadorSessionIdAndStatus(@Param("tenantId") Long tenantId,
                                                                             @Param("caixaId") Long caixaId,
                                                                             @Param("status") OrdemPagamentoStatus status);
}
