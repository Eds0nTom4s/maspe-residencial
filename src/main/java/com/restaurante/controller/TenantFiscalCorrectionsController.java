package com.restaurante.controller;

import com.restaurante.dto.request.FiscalAssessmentDecisionRequest;
import com.restaurante.dto.request.FiscalCorrectionIssueRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.FiscalAdjustmentAssessmentResponse;
import com.restaurante.fiscal.corrections.service.FiscalCorrectionAdminService;
import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import com.restaurante.model.enums.FiscalAdjustmentImpactType;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant/fiscal/adjustment-assessments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Fiscal Corrections", description = "Avaliação de impacto fiscal de ajustes e emissão de notas internas (crédito/débito)")
public class TenantFiscalCorrectionsController {

    private final FiscalCorrectionAdminService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FiscalAdjustmentAssessmentResponse>>> list(
            @RequestParam(name = "status", required = false) FiscalAdjustmentAssessmentStatus status,
            @RequestParam(name = "impactType", required = false) FiscalAdjustmentImpactType impactType,
            @RequestParam(name = "caixaAdjustmentId", required = false) Long caixaAdjustmentId,
            @RequestParam(name = "originalFiscalDocumentId", required = false) Long originalFiscalDocumentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Assessments", service.listAssessments(status, impactType, caixaAdjustmentId, originalFiscalDocumentId, pageable)));
    }

    @GetMapping("/{assessmentId}")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> get(@PathVariable Long assessmentId) {
        return ResponseEntity.ok(ApiResponse.success("Assessment", service.getAssessment(assessmentId)));
    }

    @PostMapping("/{assessmentId}/mark-no-impact")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> markNoImpact(@PathVariable Long assessmentId,
                                                                                        @RequestBody(required = false) FiscalAssessmentDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Assessment atualizado", service.markNoImpact(assessmentId, request)));
    }

    @PostMapping("/{assessmentId}/require-credit-note")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> requireCredit(@PathVariable Long assessmentId,
                                                                                         @RequestBody(required = false) FiscalAssessmentDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Assessment atualizado", service.requireCreditNote(assessmentId, request)));
    }

    @PostMapping("/{assessmentId}/require-debit-note")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> requireDebit(@PathVariable Long assessmentId,
                                                                                        @RequestBody(required = false) FiscalAssessmentDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Assessment atualizado", service.requireDebitNote(assessmentId, request)));
    }

    @PostMapping("/{assessmentId}/issue-credit-note")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> issueCredit(@PathVariable Long assessmentId,
                                                                                       @RequestBody FiscalCorrectionIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Documento corretivo emitido", service.issueCreditNote(assessmentId, request)));
    }

    @PostMapping("/{assessmentId}/issue-debit-note")
    public ResponseEntity<ApiResponse<FiscalAdjustmentAssessmentResponse>> issueDebit(@PathVariable Long assessmentId,
                                                                                      @RequestBody FiscalCorrectionIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Documento corretivo emitido", service.issueDebitNote(assessmentId, request)));
    }
}

