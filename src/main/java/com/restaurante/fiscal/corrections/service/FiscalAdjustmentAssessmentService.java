package com.restaurante.fiscal.corrections.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.repository.FiscalAdjustmentAssessmentRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.model.entity.CaixaOperadorAdjustment;
import com.restaurante.model.entity.FiscalAdjustmentAssessment;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.TenantFiscalCorrectionPolicy;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.enums.*;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FiscalAdjustmentAssessmentService {

    private final TaxProperties taxProperties;
    private final TenantFiscalProfileRepository fiscalProfileRepository;
    private final CaixaOperadorAdjustmentRepository adjustmentRepository;
    private final FiscalAdjustmentAssessmentRepository assessmentRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final TenantFiscalCorrectionPolicyService correctionPolicyService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FiscalAdjustmentAssessment createAssessmentForApprovedAdjustment(Long tenantId, Long adjustmentId) {
        if (tenantId == null || adjustmentId == null) return null;
        if (!taxProperties.isEnabled()) return null;

        TenantFiscalProfile profile = fiscalProfileRepository.findByTenantId(tenantId).orElse(null);
        if (profile == null || profile.getFiscalRegime() == FiscalRegime.NOT_CONFIGURED || !profile.isFiscalDocumentEnabled()) {
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_SKIPPED,
                    OperationalEntityType.CAIXA_OPERADOR_SESSION,
                    adjustmentId,
                    OperationalOrigem.SYSTEM,
                    "Assessment fiscal skip: perfil fiscal ausente/inativo ou documento fiscal desativado",
                    Map.of("tenantId", tenantId, "adjustmentId", adjustmentId),
                    null,
                    null
            );
            return null;
        }

        TenantFiscalCorrectionPolicy policy = correctionPolicyService.resolveActiveOrDefault(tenantId, LocalDateTime.now());
        if (!policy.isAutoAssessAdjustments()) {
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_SKIPPED,
                    OperationalEntityType.CAIXA_OPERADOR_SESSION,
                    adjustmentId,
                    OperationalOrigem.SYSTEM,
                    "Assessment fiscal skip: policy autoAssessAdjustments=false",
                    Map.of("tenantId", tenantId, "adjustmentId", adjustmentId),
                    null,
                    null
            );
            return null;
        }

        FiscalAdjustmentAssessment existing = assessmentRepository.findByTenantIdAndAdjustmentId(tenantId, adjustmentId).orElse(null);
        if (existing != null) return existing;

        CaixaOperadorAdjustment adj = adjustmentRepository.findById(adjustmentId).orElse(null);
        if (adj == null || adj.getTenant() == null || adj.getTenant().getId() == null || !adj.getTenant().getId().equals(tenantId)) {
            return null;
        }
        if (adj.getStatus() != CaixaOperadorAdjustmentStatus.APPROVED) {
            return null;
        }

        FiscalAdjustmentAssessment assessment = new FiscalAdjustmentAssessment();
        assessment.setTenant(adj.getTenant());
        assessment.setAdjustment(adj);
        assessment.setDivergence(adj.getDivergence());
        assessment.setCaixaOperadorSession(adj.getCaixaOperadorSession());
        assessment.setTurnoOperacional(adj.getDivergence() != null ? adj.getDivergence().getTurnoOperacional() : null);
        assessment.setUnidadeAtendimento(adj.getDivergence() != null ? adj.getDivergence().getUnidadeAtendimento() : null);

        var decision = initialDecision(adj, policy);
        assessment.setImpactType(decision.impactType());
        assessment.setStatus(decision.status());
        assessment.setDecisionReason(decision.reason());
        assessment.setAssessedAt(LocalDateTime.now());

        // Tentativa best-effort de relacionar com documento original:
        // se já existir documento emitido amarrado ao adjustment/assessment, manter; caso contrário, deixar null para decisão manual.
        FiscalDocument alreadyLinked = fiscalDocumentRepository.findByTenantIdAndCaixaOperadorAdjustmentId(tenantId, adj.getId()).orElse(null);
        if (alreadyLinked != null) {
            assessment.setOriginalFiscalDocument(alreadyLinked.getOriginalFiscalDocument());
        }

        try {
            assessment = assessmentRepository.save(assessment);
        } catch (DataIntegrityViolationException ex) {
            FiscalAdjustmentAssessment dup = assessmentRepository.findByTenantIdAndAdjustmentId(tenantId, adjustmentId).orElse(null);
            if (dup != null) return dup;
            throw ex;
        }

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_CREATED,
                OperationalEntityType.FISCAL_ADJUSTMENT_ASSESSMENT,
                assessment.getId(),
                OperationalOrigem.SYSTEM,
                "Assessment fiscal criado para ajuste aprovado",
                Map.of(
                        "tenantId", tenantId,
                        "assessmentId", assessment.getId(),
                        "adjustmentId", adj.getId(),
                        "status", assessment.getStatus().name(),
                        "impactType", assessment.getImpactType().name()
                ),
                null,
                null
        );

        return assessment;
    }

    private static Decision initialDecision(CaixaOperadorAdjustment adj, TenantFiscalCorrectionPolicy policy) {
        if (adj == null) {
            return new Decision(FiscalAdjustmentAssessmentStatus.PENDING, FiscalAdjustmentImpactType.MANUAL_REVIEW_REQUIRED, "NULL_ADJUSTMENT");
        }

        if (adj.getDirection() == CaixaOperadorAdjustmentDirection.NO_LEDGER_IMPACT) {
            return new Decision(FiscalAdjustmentAssessmentStatus.NO_FISCAL_IMPACT, FiscalAdjustmentImpactType.NO_TAX_IMPACT, "NO_LEDGER_IMPACT");
        }
        if (adj.getAdjustmentType() == CaixaOperadorAdjustmentType.METHOD_RECLASSIFICATION) {
            return new Decision(FiscalAdjustmentAssessmentStatus.NO_FISCAL_IMPACT, FiscalAdjustmentImpactType.RECLASSIFY_ONLY, "METHOD_RECLASSIFICATION");
        }

        if (adj.getAdjustmentType() == CaixaOperadorAdjustmentType.ADMIN_CORRECTION) {
            return switch (adj.getDirection()) {
                case DECREASE_EXPECTED, DECREASE_DECLARED -> new Decision(
                        FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE,
                        FiscalAdjustmentImpactType.REDUCE_TAXABLE_AMOUNT,
                        "ADMIN_CORRECTION_DECREASE"
                );
                case INCREASE_EXPECTED, INCREASE_DECLARED -> new Decision(
                        FiscalAdjustmentAssessmentStatus.REQUIRES_DEBIT_NOTE,
                        FiscalAdjustmentImpactType.INCREASE_TAXABLE_AMOUNT,
                        "ADMIN_CORRECTION_INCREASE"
                );
                case NO_LEDGER_IMPACT -> new Decision(FiscalAdjustmentAssessmentStatus.NO_FISCAL_IMPACT, FiscalAdjustmentImpactType.NO_TAX_IMPACT, "ADMIN_CORRECTION_NO_LEDGER_IMPACT");
            };
        }

        if (adj.getAdjustmentType() == CaixaOperadorAdjustmentType.ACCEPTED_LOSS && policy.isRequireManualReviewForLoss()) {
            return new Decision(FiscalAdjustmentAssessmentStatus.PENDING, FiscalAdjustmentImpactType.MANUAL_REVIEW_REQUIRED, "ACCEPTED_LOSS_REVIEW_REQUIRED");
        }
        if (adj.getAdjustmentType() == CaixaOperadorAdjustmentType.ACCEPTED_SURPLUS && policy.isRequireManualReviewForSurplus()) {
            return new Decision(FiscalAdjustmentAssessmentStatus.PENDING, FiscalAdjustmentImpactType.MANUAL_REVIEW_REQUIRED, "ACCEPTED_SURPLUS_REVIEW_REQUIRED");
        }

        return new Decision(FiscalAdjustmentAssessmentStatus.PENDING, FiscalAdjustmentImpactType.MANUAL_REVIEW_REQUIRED, "DEFAULT_MANUAL_REVIEW");
    }

    private record Decision(FiscalAdjustmentAssessmentStatus status, FiscalAdjustmentImpactType impactType, String reason) {}
}
