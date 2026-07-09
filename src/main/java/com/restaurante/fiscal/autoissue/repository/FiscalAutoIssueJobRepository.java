package com.restaurante.fiscal.autoissue.repository;

import com.restaurante.model.entity.FiscalAutoIssueJob;
import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FiscalAutoIssueJobRepository extends JpaRepository<FiscalAutoIssueJob, Long> {

    Optional<FiscalAutoIssueJob> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    Optional<FiscalAutoIssueJob> findFirstByTenantIdAndPagamentoIdOrderByCreatedAtDesc(Long tenantId, Long pagamentoId);

    @Query("""
            select j from FiscalAutoIssueJob j
            where j.tenant.id = :tenantId
              and (:status is null or j.status = :status)
              and (:pedidoId is null or j.pedido.id = :pedidoId)
              and (:pagamentoId is null or j.pagamento.id = :pagamentoId)
              and (:fiscalDocumentId is null or j.fiscalDocument.id = :fiscalDocumentId)
            order by j.createdAt desc
            """)
    Page<FiscalAutoIssueJob> listByTenant(@Param("tenantId") Long tenantId,
                                          @Param("status") FiscalAutoIssueJobStatus status,
                                          @Param("pedidoId") Long pedidoId,
                                          @Param("pagamentoId") Long pagamentoId,
                                          @Param("fiscalDocumentId") Long fiscalDocumentId,
                                          Pageable pageable);

    @Query("""
            select j from FiscalAutoIssueJob j
            where j.status in (:statuses)
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
            order by j.nextAttemptAt asc nulls first, j.id asc
            """)
    List<FiscalAutoIssueJob> findDueJobs(@Param("statuses") List<FiscalAutoIssueJobStatus> statuses,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    @Modifying
    @Query("""
            update FiscalAutoIssueJob j
               set j.lockedAt = :lockedAt,
                   j.lockedBy = :lockedBy
             where j.id = :jobId
               and (j.lockedAt is null or j.lockedAt < :staleCutoff)
               and j.status in (:allowedStatuses)
            """)
    int claimJob(@Param("jobId") Long jobId,
                 @Param("lockedAt") LocalDateTime lockedAt,
                 @Param("lockedBy") String lockedBy,
                 @Param("staleCutoff") LocalDateTime staleCutoff,
                 @Param("allowedStatuses") List<FiscalAutoIssueJobStatus> allowedStatuses);

    @Modifying
    @Query("""
            update FiscalAutoIssueJob j
               set j.status = :newStatus,
                   j.lockedAt = null,
                   j.lockedBy = null,
                   j.errorCode = :errorCode,
                   j.errorMessage = :errorMessage,
                   j.nextAttemptAt = :nextAttemptAt
             where j.status = :processingStatus
               and j.lockedAt is not null
               and j.lockedAt < :staleCutoff
            """)
    int recoverStaleProcessingJobs(@Param("processingStatus") FiscalAutoIssueJobStatus processingStatus,
                                   @Param("newStatus") FiscalAutoIssueJobStatus newStatus,
                                   @Param("staleCutoff") LocalDateTime staleCutoff,
                                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                                   @Param("errorCode") String errorCode,
                                   @Param("errorMessage") String errorMessage);

    @Query("""
            select j.id
            from FiscalAutoIssueJob j
            where j.status = :processingStatus
              and j.lockedAt is not null
              and j.lockedAt < :staleCutoff
            order by j.lockedAt asc, j.id asc
            """)
    List<Long> findStaleProcessingJobIds(@Param("processingStatus") FiscalAutoIssueJobStatus processingStatus,
                                         @Param("staleCutoff") LocalDateTime staleCutoff,
                                         Pageable pageable);

    @Query("""
            select j.status as status, count(j) as cnt
            from FiscalAutoIssueJob j
            where j.tenant.id = :tenantId
              and j.pedido.turnoOperacional.id = :turnoId
            group by j.status
            """)
    List<Object[]> countByTenantAndTurnoGroupByStatus(@Param("tenantId") Long tenantId,
                                                      @Param("turnoId") Long turnoId);
}
