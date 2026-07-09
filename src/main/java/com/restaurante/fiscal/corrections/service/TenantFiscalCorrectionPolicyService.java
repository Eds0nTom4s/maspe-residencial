package com.restaurante.fiscal.corrections.service;

import com.restaurante.fiscal.repository.TenantFiscalCorrectionPolicyRepository;
import com.restaurante.model.entity.TenantFiscalCorrectionPolicy;
import com.restaurante.model.enums.TenantFiscalCorrectionPolicyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantFiscalCorrectionPolicyService {

    private final TenantFiscalCorrectionPolicyRepository repository;

    public TenantFiscalCorrectionPolicy resolveActiveOrDefault(Long tenantId, LocalDateTime at) {
        if (tenantId == null) return defaultPolicy(null);
        return repository.findActiveEffective(tenantId, TenantFiscalCorrectionPolicyStatus.ACTIVE, at != null ? at : LocalDateTime.now())
                .orElseGet(() -> defaultPolicy(tenantId));
    }

    private static TenantFiscalCorrectionPolicy defaultPolicy(Long tenantId) {
        TenantFiscalCorrectionPolicy p = new TenantFiscalCorrectionPolicy();
        // tenant é opcional no default in-memory; o serviço chamador deve tratar tenantId.
        p.setStatus(TenantFiscalCorrectionPolicyStatus.ACTIVE);
        p.setAutoAssessAdjustments(true);
        p.setAutoIssueCreditNote(false);
        p.setAutoIssueDebitNote(false);
        p.setRequireManualReviewForLoss(true);
        p.setRequireManualReviewForSurplus(true);
        p.setAllowCorrectionWithoutOriginalDocument(false);
        p.setMaxAutoCorrectionAmount(java.math.BigDecimal.ZERO);
        return p;
    }
}

