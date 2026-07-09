package com.restaurante.txevidence.repository;

import com.restaurante.model.entity.TransactionEvidenceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionEvidenceEventRepository extends JpaRepository<TransactionEvidenceEvent, Long> {
    Optional<TransactionEvidenceEvent> findByTenantIdAndId(Long tenantId, Long id);
    Optional<TransactionEvidenceEvent> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
    List<TransactionEvidenceEvent> findByTenantIdAndOccurredAtBetweenOrderByLedgerSequenceAsc(Long tenantId, LocalDateTime from, LocalDateTime to);
    Page<TransactionEvidenceEvent> findByTenantIdOrderByLedgerSequenceDesc(Long tenantId, Pageable pageable);
    Optional<TransactionEvidenceEvent> findTopByTenantIdOrderByLedgerSequenceDesc(Long tenantId);
    Optional<TransactionEvidenceEvent> findTopByTenantIdOrderByLedgerSequenceAsc(Long tenantId);

    Optional<TransactionEvidenceEvent> findByTenantIdAndLedgerSequence(Long tenantId, Long ledgerSequence);

    @Query("""
            select e from TransactionEvidenceEvent e
            where e.tenant.id = :tenantId
              and (:eventType is null or e.eventType = :eventType)
              and (:sourceModule is null or e.sourceModule = :sourceModule)
              and (:sourceEntityType is null or e.sourceEntityType = :sourceEntityType)
              and (:sourceEntityId is null or e.sourceEntityId = :sourceEntityId)
              and (:occurredFrom is null or e.occurredAt >= :occurredFrom)
              and (:occurredTo is null or e.occurredAt <= :occurredTo)
              and (:seqFrom is null or e.ledgerSequence >= :seqFrom)
              and (:seqTo is null or e.ledgerSequence <= :seqTo)
            order by e.ledgerSequence desc
            """)
    Page<TransactionEvidenceEvent> search(Long tenantId,
                                         String eventType,
                                         com.restaurante.model.enums.TransactionEvidenceSourceModule sourceModule,
                                         String sourceEntityType,
                                         Long sourceEntityId,
                                         LocalDateTime occurredFrom,
                                         LocalDateTime occurredTo,
                                         Long seqFrom,
                                         Long seqTo,
                                         Pageable pageable);
}
