package com.restaurante.txevidence.repository;

import com.restaurante.model.entity.TransactionEvidenceVerificationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionEvidenceVerificationRunRepository extends JpaRepository<TransactionEvidenceVerificationRun, Long> {
    Optional<TransactionEvidenceVerificationRun> findByTenantIdAndId(Long tenantId, Long id);
    List<TransactionEvidenceVerificationRun> findByTenantIdOrderByStartedAtDesc(Long tenantId);
    Optional<TransactionEvidenceVerificationRun> findTopByTenantIdOrderByStartedAtDesc(Long tenantId);
}

