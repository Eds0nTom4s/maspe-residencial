package com.restaurante.fiscal.corrections.service;

import com.restaurante.dto.request.FiscalAssessmentDecisionRequest;
import com.restaurante.dto.request.FiscalCorrectionIssueRequest;
import com.restaurante.dto.response.FiscalAdjustmentAssessmentResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.corrections.event.FiscalCreditNoteIssuedForInventoryReturnEvent;
import com.restaurante.fiscal.repository.FiscalAdjustmentAssessmentRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.service.FiscalDocumentSequenceService;
import com.restaurante.model.entity.FiscalAdjustmentAssessment;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.*;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FiscalCorrectionAdminService {

    private final TenantGuard tenantGuard;
    private final UserRepository userRepository;
    private final FiscalAdjustmentAssessmentRepository assessmentRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;
    private final FiscalDocumentSequenceService sequenceService;
    private final FiscalCorrectionCalculationService calcService;
    private final OperationalEventLogService operationalEventLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<FiscalAdjustmentAssessmentResponse> listAssessments(FiscalAdjustmentAssessmentStatus status,
                                                                   FiscalAdjustmentImpactType impactType,
                                                                   Long caixaAdjustmentId,
                                                                   Long originalFiscalDocumentId,
                                                                   Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        return assessmentRepository
                .listByTenant(ctx.tenantId(), status, impactType, caixaAdjustmentId, originalFiscalDocumentId, pageable)
                .map(a -> toResponse(a, null));
    }

    @Transactional(readOnly = true)
    public FiscalAdjustmentAssessmentResponse getAssessment(Long assessmentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        FiscalAdjustmentAssessment a = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new BusinessException("Assessment não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(a.getTenant().getId());

        FiscalDocument correction = fiscalDocumentRepository.findByTenantIdAndFiscalAdjustmentAssessmentId(ctx.tenantId(), a.getId()).orElse(null);
        return toResponse(a, correction);
    }

    @Transactional
    public FiscalAdjustmentAssessmentResponse markNoImpact(Long assessmentId, FiscalAssessmentDecisionRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        FiscalAdjustmentAssessment a = assessmentRepository.findByIdForUpdate(assessmentId)
                .orElseThrow(() -> new BusinessException("Assessment não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(a.getTenant().getId());

        a.setStatus(FiscalAdjustmentAssessmentStatus.NO_FISCAL_IMPACT);
        a.setImpactType(FiscalAdjustmentImpactType.NO_TAX_IMPACT);
        a.setDecisionReason(safeReason(request != null ? request.getReason() : null, "NO_IMPACT"));
        User user = userRepository.findById(ctx.userId()).orElse(null);
        a.setAssessedBy(user);
        a.setAssessedAt(LocalDateTime.now());
        assessmentRepository.save(a);

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_MARKED_NO_IMPACT,
                OperationalEntityType.FISCAL_ADJUSTMENT_ASSESSMENT,
                a.getId(),
                OperationalOrigem.SYSTEM,
                "Assessment fiscal marcado como sem impacto",
                Map.of("assessmentId", a.getId(), "adjustmentId", a.getAdjustment().getId()),
                null,
                null
        );

        return toResponse(a, null);
    }

    @Transactional
    public FiscalAdjustmentAssessmentResponse requireCreditNote(Long assessmentId, FiscalAssessmentDecisionRequest request) {
        return require(assessmentId, request, FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE, FiscalAdjustmentImpactType.REDUCE_TAXABLE_AMOUNT,
                OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_REQUIRES_CREDIT_NOTE);
    }

    @Transactional
    public FiscalAdjustmentAssessmentResponse requireDebitNote(Long assessmentId, FiscalAssessmentDecisionRequest request) {
        return require(assessmentId, request, FiscalAdjustmentAssessmentStatus.REQUIRES_DEBIT_NOTE, FiscalAdjustmentImpactType.INCREASE_TAXABLE_AMOUNT,
                OperationalEventType.FISCAL_ADJUSTMENT_ASSESSMENT_REQUIRES_DEBIT_NOTE);
    }

    private FiscalAdjustmentAssessmentResponse require(Long assessmentId,
                                                      FiscalAssessmentDecisionRequest request,
                                                      FiscalAdjustmentAssessmentStatus newStatus,
                                                      FiscalAdjustmentImpactType impactType,
                                                      OperationalEventType eventType) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        FiscalAdjustmentAssessment a = assessmentRepository.findByIdForUpdate(assessmentId)
                .orElseThrow(() -> new BusinessException("Assessment não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(a.getTenant().getId());

        Long originalId = request != null ? request.getOriginalFiscalDocumentId() : null;
        if (originalId != null) {
            FiscalDocument orig = fiscalDocumentRepository.findById(originalId).orElseThrow(() -> new BusinessException("Documento original não encontrado."));
            tenantGuard.assertResourceBelongsToTenant(orig.getTenant().getId());
            if (orig.getStatus() != FiscalDocumentStatus.ISSUED) {
                throw new BusinessException("Documento original deve estar ISSUED.");
            }
            a.setOriginalFiscalDocument(orig);
        } else if (a.getOriginalFiscalDocument() == null) {
            throw new BusinessException("originalFiscalDocumentId é obrigatório para exigir nota.");
        }

        a.setStatus(newStatus);
        a.setImpactType(impactType);
        a.setDecisionReason(safeReason(request != null ? request.getReason() : null, newStatus.name()));
        User user = userRepository.findById(ctx.userId()).orElse(null);
        a.setAssessedBy(user);
        a.setAssessedAt(LocalDateTime.now());
        assessmentRepository.save(a);

        operationalEventLogService.logGeneric(
                eventType,
                OperationalEntityType.FISCAL_ADJUSTMENT_ASSESSMENT,
                a.getId(),
                OperationalOrigem.SYSTEM,
                "Assessment fiscal atualizado",
                Map.of("assessmentId", a.getId(), "status", a.getStatus().name(), "impactType", a.getImpactType().name()),
                null,
                null
        );

        return toResponse(a, null);
    }

    @Transactional
    public FiscalAdjustmentAssessmentResponse issueCreditNote(Long assessmentId, FiscalCorrectionIssueRequest request) {
        return issueCorrection(assessmentId, request, FiscalDocumentType.INTERNAL_CREDIT_NOTE,
                FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE, OperationalEventType.FISCAL_INTERNAL_CREDIT_NOTE_ISSUED);
    }

    @Transactional
    public FiscalAdjustmentAssessmentResponse issueDebitNote(Long assessmentId, FiscalCorrectionIssueRequest request) {
        return issueCorrection(assessmentId, request, FiscalDocumentType.INTERNAL_DEBIT_NOTE,
                FiscalAdjustmentAssessmentStatus.REQUIRES_DEBIT_NOTE, OperationalEventType.FISCAL_INTERNAL_DEBIT_NOTE_ISSUED);
    }

    private FiscalAdjustmentAssessmentResponse issueCorrection(Long assessmentId,
                                                              FiscalCorrectionIssueRequest request,
                                                              FiscalDocumentType docType,
                                                              FiscalAdjustmentAssessmentStatus requiredStatus,
                                                              OperationalEventType issuedEventType) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        if (request == null || request.getAmount() == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("amount e reason são obrigatórios.");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("amount deve ser > 0.");
        if (request.getLineMode() != null && request.getLineMode() != FiscalCorrectionLineMode.SINGLE_ADJUSTMENT_LINE) {
            throw new BusinessException("Apenas SINGLE_ADJUSTMENT_LINE está suportado no MVP.");
        }

        FiscalAdjustmentAssessment a = assessmentRepository.findByIdForUpdate(assessmentId)
                .orElseThrow(() -> new BusinessException("Assessment não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(a.getTenant().getId());

        if (a.getStatus() != requiredStatus) {
            throw new BusinessException("Assessment em estado inválido para emissão: " + a.getStatus());
        }

        FiscalDocument existing = fiscalDocumentRepository.findByTenantIdAndFiscalAdjustmentAssessmentId(ctx.tenantId(), a.getId()).orElse(null);
        if (existing != null) {
            return toResponse(a, existing);
        }

        FiscalDocument original = a.getOriginalFiscalDocument();
        if (original == null && request.getOriginalFiscalDocumentId() != null) {
            original = fiscalDocumentRepository.findById(request.getOriginalFiscalDocumentId())
                    .orElseThrow(() -> new BusinessException("Documento original não encontrado."));
            tenantGuard.assertResourceBelongsToTenant(original.getTenant().getId());
            a.setOriginalFiscalDocument(original);
            assessmentRepository.save(a);
        }
        if (original == null) throw new BusinessException("Documento original é obrigatório para emitir correção.");
        if (original.getStatus() != FiscalDocumentStatus.ISSUED) throw new BusinessException("Documento original deve estar ISSUED.");

        // carregar linhas do original (evita lazy issues)
        List<FiscalDocumentLine> originalLines = fiscalDocumentLineRepository.findByTenantIdAndFiscalDocumentId(ctx.tenantId(), original.getId());
        original.setLines(originalLines);

        var amounts = calcService.calculateSingleLineGrossAmount(original, request.getAmount());

        LocalDateTime issuedAt = LocalDateTime.now();
        String series = docType == FiscalDocumentType.INTERNAL_CREDIT_NOTE ? "C" : "D";
        String number = sequenceService.nextNumber(ctx.tenantId(),
                original.getUnidadeAtendimento() != null ? original.getUnidadeAtendimento().getId() : null,
                docType,
                series,
                issuedAt);

        FiscalDocument correction = new FiscalDocument();
        correction.setTenant(original.getTenant());
        correction.setInstituicao(original.getInstituicao());
        correction.setUnidadeAtendimento(original.getUnidadeAtendimento());
        correction.setTurnoOperacional(original.getTurnoOperacional());
        correction.setSessaoConsumo(original.getSessaoConsumo());
        correction.setPedido(original.getPedido());
        correction.setPagamento(original.getPagamento());
        correction.setCaixaOperadorSession(original.getCaixaOperadorSession());
        correction.setOriginalFiscalDocument(original);
        correction.setFiscalAdjustmentAssessment(a);
        correction.setCaixaOperadorAdjustment(a.getAdjustment());
        correction.setCorrectionSource(FiscalCorrectionSource.CAIXA_OPERADOR_ADJUSTMENT);
        correction.setCorrectionReason(safeReason(request.getReason(), "CORRECTION"));
        correction.setDocumentType(docType);
        correction.setStatus(FiscalDocumentStatus.ISSUED);
        correction.setFiscalRegime(original.getFiscalRegime());
        correction.setSeries(series);
        correction.setDocumentNumber(number);
        correction.setIssuedAt(issuedAt);
        correction.setSubtotalAmount(amounts.netAmount());
        correction.setTaxableAmount(amounts.netAmount());
        correction.setExemptAmount(BigDecimal.ZERO);
        correction.setTaxAmount(amounts.taxAmount());
        correction.setTotalAmount(amounts.totalAmount());
        correction.setCurrency(original.getCurrency() != null ? original.getCurrency() : "AOA");
        correction.setSource(FiscalDocumentSource.ADMIN);

        try {
            correction = fiscalDocumentRepository.save(correction);
        } catch (DataIntegrityViolationException ex) {
            FiscalDocument dup = fiscalDocumentRepository.findByTenantIdAndFiscalAdjustmentAssessmentId(ctx.tenantId(), a.getId()).orElse(null);
            if (dup != null) correction = dup;
            else throw ex;
        }

        FiscalDocumentLine line = new FiscalDocumentLine();
        line.setFiscalDocument(correction);
        line.setTenant(correction.getTenant());
        line.setDescription("Correção: " + docType.name());
        line.setQuantity(1);
        line.setUnitPrice(amounts.totalAmount());
        line.setNetAmount(amounts.netAmount());
        line.setTaxRateCode(originalLines.isEmpty() ? null : originalLines.get(0).getTaxRateCode());
        line.setTaxRateValue(amounts.taxRateValue());
        line.setTaxAmount(amounts.taxAmount());
        line.setGrossAmount(amounts.totalAmount());
        line.setTaxCategory(TaxCategory.STANDARD);
        fiscalDocumentLineRepository.save(line);

        a.setStatus(FiscalAdjustmentAssessmentStatus.CORRECTION_ISSUED);
        a.setAssessedBy(userRepository.findById(ctx.userId()).orElse(null));
        a.setAssessedAt(LocalDateTime.now());
        assessmentRepository.save(a);

        operationalEventLogService.logGeneric(
                issuedEventType,
                OperationalEntityType.FISCAL_DOCUMENT,
                correction.getId(),
                OperationalOrigem.SYSTEM,
                "Documento corretivo fiscal interno emitido",
                Map.of(
                        "assessmentId", a.getId(),
                        "correctionFiscalDocumentId", correction.getId(),
                        "originalFiscalDocumentId", original.getId(),
                        "documentType", correction.getDocumentType().name(),
                        "amount", correction.getTotalAmount()
                ),
                null,
                null
        );

        if (docType == FiscalDocumentType.INTERNAL_CREDIT_NOTE
                && (correction.getCorrectionSource() == FiscalCorrectionSource.PRODUCT_RETURN
                || correction.getCorrectionSource() == FiscalCorrectionSource.PARTIAL_REFUND)) {
            eventPublisher.publishEvent(new FiscalCreditNoteIssuedForInventoryReturnEvent(
                    ctx.tenantId(),
                    correction.getId(),
                    original.getId(),
                    correction.getPedido() != null ? correction.getPedido().getId() : null,
                    correction.getPagamento() != null ? correction.getPagamento().getId() : null,
                    correction.getTotalAmount(),
                    correction.getIssuedAt(),
                    correction.getCorrectionSource()
            ));
        }

        return toResponse(a, correction);
    }

    private FiscalAdjustmentAssessmentResponse toResponse(FiscalAdjustmentAssessment a, FiscalDocument correction) {
        FiscalAdjustmentAssessmentResponse r = new FiscalAdjustmentAssessmentResponse();
        r.setId(a.getId());
        r.setTenantId(a.getTenant() != null ? a.getTenant().getId() : null);
        r.setCaixaOperadorAdjustmentId(a.getAdjustment() != null ? a.getAdjustment().getId() : null);
        r.setCaixaOperadorDivergenceId(a.getDivergence() != null ? a.getDivergence().getId() : null);
        r.setCaixaOperadorSessionId(a.getCaixaOperadorSession() != null ? a.getCaixaOperadorSession().getId() : null);
        r.setTurnoOperacionalId(a.getTurnoOperacional() != null ? a.getTurnoOperacional().getId() : null);
        r.setUnidadeAtendimentoId(a.getUnidadeAtendimento() != null ? a.getUnidadeAtendimento().getId() : null);
        r.setOriginalFiscalDocumentId(a.getOriginalFiscalDocument() != null ? a.getOriginalFiscalDocument().getId() : null);
        r.setStatus(a.getStatus());
        r.setImpactType(a.getImpactType());
        r.setDecisionReason(a.getDecisionReason());
        r.setAssessedByUserId(a.getAssessedBy() != null ? a.getAssessedBy().getId() : null);
        r.setAssessedAt(a.getAssessedAt());
        r.setCorrectionFiscalDocumentId(correction != null ? correction.getId() : null);
        return r;
    }

    private static String safeReason(String reason, String fallback) {
        String r = reason != null ? reason.replaceAll("[\\r\\n\\t]", " ").trim() : null;
        if (r == null || r.isBlank()) r = fallback;
        if (r.length() > 500) r = r.substring(0, 500);
        return r;
    }
}
