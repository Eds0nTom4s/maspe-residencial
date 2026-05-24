package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.enums.FiscalDocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {

    @Query("""
            select d from FiscalDocument d
            where d.tenant.id = :tenantId
              and (:status is null or d.status = :status)
            order by d.issuedAt desc nulls last, d.createdAt desc
            """)
    Page<FiscalDocument> listByTenant(@Param("tenantId") Long tenantId,
                                      @Param("status") FiscalDocumentStatus status,
                                      Pageable pageable);

    Optional<FiscalDocument> findByTenantIdAndPedidoIdAndPagamentoId(Long tenantId, Long pedidoId, Long pagamentoId);

    Optional<FiscalDocument> findByTenantIdAndPagamentoId(Long tenantId, Long pagamentoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FiscalDocument d where d.id = :id")
    Optional<FiscalDocument> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select d from FiscalDocument d
            where d.tenant.id = :tenantId
              and d.turnoOperacional.id = :turnoId
            order by d.issuedAt asc nulls last, d.id asc
            """)
    List<FiscalDocument> findAllByTenantIdAndTurnoOperacionalId(@Param("tenantId") Long tenantId,
                                                                @Param("turnoId") Long turnoId);
}

