package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.FiscalAdjustmentAssessment;
import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FiscalAdjustmentAssessmentRepository extends JpaRepository<FiscalAdjustmentAssessment, Long> {

    Optional<FiscalAdjustmentAssessment> findByTenantIdAndAdjustmentId(Long tenantId, Long adjustmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FiscalAdjustmentAssessment a where a.id = :id")
    Optional<FiscalAdjustmentAssessment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select a from FiscalAdjustmentAssessment a
            where a.tenant.id = :tenantId
              and (:status is null or a.status = :status)
              and (:impactType is null or a.impactType = :impactType)
              and (:caixaAdjustmentId is null or a.adjustment.id = :caixaAdjustmentId)
              and (:originalFiscalDocumentId is null or a.originalFiscalDocument.id = :originalFiscalDocumentId)
            order by a.createdAt desc
            """)
    Page<FiscalAdjustmentAssessment> listByTenant(@Param("tenantId") Long tenantId,
                                                 @Param("status") FiscalAdjustmentAssessmentStatus status,
                                                 @Param("impactType") com.restaurante.model.enums.FiscalAdjustmentImpactType impactType,
                                                 @Param("caixaAdjustmentId") Long caixaAdjustmentId,
                                                 @Param("originalFiscalDocumentId") Long originalFiscalDocumentId,
                                                 Pageable pageable);

    @Query("""
            select a.status as status, count(a) as cnt
            from FiscalAdjustmentAssessment a
            where a.tenant.id = :tenantId
              and a.turnoOperacional.id = :turnoId
            group by a.status
            """)
    java.util.List<Object[]> countByTenantAndTurnoGroupByStatus(@Param("tenantId") Long tenantId,
                                                                @Param("turnoId") Long turnoId);

    @Query("""
            select a from FiscalAdjustmentAssessment a
            where a.tenant.id = :tenantId
              and a.turnoOperacional.id = :turnoId
            order by a.createdAt asc, a.id asc
            """)
    java.util.List<FiscalAdjustmentAssessment> findAllByTenantIdAndTurnoOperacionalId(@Param("tenantId") Long tenantId,
                                                                                      @Param("turnoId") Long turnoId);
}
