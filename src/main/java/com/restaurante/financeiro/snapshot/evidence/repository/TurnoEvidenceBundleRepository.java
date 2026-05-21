package com.restaurante.financeiro.snapshot.evidence.repository;

import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TurnoEvidenceBundleRepository extends JpaRepository<TurnoEvidenceBundle, Long> {

    Page<TurnoEvidenceBundle> findByTenantIdAndTurnoIdOrderBySequenceNumberDesc(Long tenantId, Long turnoId, Pageable pageable);

    Optional<TurnoEvidenceBundle> findByIdAndTenantIdAndTurnoId(Long id, Long tenantId, Long turnoId);

    Optional<TurnoEvidenceBundle> findTopByTenantIdAndTurnoIdOrderBySequenceNumberDesc(Long tenantId, Long turnoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from TurnoEvidenceBundle b
            where b.tenant.id = :tenantId
              and b.turno.id = :turnoId
            order by b.sequenceNumber desc
            """)
    Page<TurnoEvidenceBundle> findLastForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("turnoId") Long turnoId,
                                               Pageable pageable);
}

