package com.restaurante.fiscal.official.repository;

import com.restaurante.model.entity.OfficialFiscalSubmission;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfficialFiscalSubmissionRepository extends JpaRepository<OfficialFiscalSubmission, Long> {
    Optional<OfficialFiscalSubmission> findByTenantIdAndFiscalDocumentId(Long tenantId, Long fiscalDocumentId);
    Optional<OfficialFiscalSubmission> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    @Query("""
            select s
            from OfficialFiscalSubmission s
            where s.tenant.id = :tenantId
              and (:status is null or s.status = :status)
            order by s.createdAt desc, s.id desc
            """)
    Page<OfficialFiscalSubmission> listByTenant(@Param("tenantId") Long tenantId,
                                               @Param("status") OfficialFiscalSubmissionStatus status,
                                               Pageable pageable);

    @Query("""
            select s
            from OfficialFiscalSubmission s
            where s.tenant.id = :tenantId
              and s.status in :statuses
              and (s.nextAttemptAt is null or s.nextAttemptAt <= :now)
            order by s.nextAttemptAt nulls first, s.id
            """)
    List<OfficialFiscalSubmission> findRunnable(@Param("tenantId") Long tenantId,
                                               @Param("statuses") Collection<OfficialFiscalSubmissionStatus> statuses,
                                               @Param("now") LocalDateTime now,
                                               Pageable pageable);

    @Query("""
            select s
            from OfficialFiscalSubmission s
            join s.fiscalDocument d
            where s.tenant.id = :tenantId
              and d.turnoOperacional.id = :turnoId
            order by s.createdAt, s.id
            """)
    List<OfficialFiscalSubmission> listByTurno(@Param("tenantId") Long tenantId, @Param("turnoId") Long turnoId);

    @Modifying
    @Query("""
            update OfficialFiscalSubmission s
               set s.lockedAt = :lockedAt,
                   s.lockedBy = :lockedBy
             where s.id = :submissionId
               and (s.lockedAt is null or s.lockedAt < :staleCutoff)
               and s.status in (:allowedStatuses)
            """)
    int claimSubmission(@Param("submissionId") Long submissionId,
                        @Param("lockedAt") LocalDateTime lockedAt,
                        @Param("lockedBy") String lockedBy,
                        @Param("staleCutoff") LocalDateTime staleCutoff,
                        @Param("allowedStatuses") List<OfficialFiscalSubmissionStatus> allowedStatuses);

    @Modifying
    @Query("""
            update OfficialFiscalSubmission s
               set s.status = :newStatus,
                   s.lockedAt = null,
                   s.lockedBy = null,
                   s.officialStatusCode = :errorCode,
                   s.officialStatusMessage = :errorMessage,
                   s.nextAttemptAt = :nextAttemptAt
             where s.status = :processingStatus
               and s.lockedAt is not null
               and s.lockedAt < :staleCutoff
            """)
    int recoverStaleProcessingSubmissions(@Param("processingStatus") OfficialFiscalSubmissionStatus processingStatus,
                                         @Param("newStatus") OfficialFiscalSubmissionStatus newStatus,
                                         @Param("staleCutoff") LocalDateTime staleCutoff,
                                         @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                                         @Param("errorCode") String errorCode,
                                         @Param("errorMessage") String errorMessage);
}
