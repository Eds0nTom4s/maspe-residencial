package com.restaurante.txevidence.repository;

import com.restaurante.model.entity.TransactionEvidenceVerificationIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionEvidenceVerificationIssueRepository extends JpaRepository<TransactionEvidenceVerificationIssue, Long> {
    List<TransactionEvidenceVerificationIssue> findByTenantIdAndVerificationRun_IdOrderByIdAsc(Long tenantId, Long runId);
}

