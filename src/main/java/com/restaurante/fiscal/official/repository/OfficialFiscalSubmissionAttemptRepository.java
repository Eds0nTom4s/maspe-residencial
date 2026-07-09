package com.restaurante.fiscal.official.repository;

import com.restaurante.model.entity.OfficialFiscalSubmissionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficialFiscalSubmissionAttemptRepository extends JpaRepository<OfficialFiscalSubmissionAttempt, Long> {
    List<OfficialFiscalSubmissionAttempt> findByTenantIdAndSubmission_IdOrderByAttemptNumber(Long tenantId, Long submissionId);
}
