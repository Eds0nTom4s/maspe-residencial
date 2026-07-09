package com.restaurante.txevidence.repository;

import com.restaurante.model.entity.TransactionEvidenceLedgerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TransactionEvidenceLedgerStateRepository extends JpaRepository<TransactionEvidenceLedgerState, Long> {
    Optional<TransactionEvidenceLedgerState> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TransactionEvidenceLedgerState s where s.tenant.id = :tenantId")
    Optional<TransactionEvidenceLedgerState> lockByTenantId(@Param("tenantId") Long tenantId);
}

