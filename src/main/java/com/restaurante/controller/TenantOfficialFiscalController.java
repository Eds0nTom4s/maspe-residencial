package com.restaurante.controller;

import com.restaurante.dto.request.OfficialFiscalSimulationDecisionRequest;
import com.restaurante.dto.request.UpsertTenantOfficialFiscalProfileRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OfficialFiscalSubmissionResponse;
import com.restaurante.dto.response.TenantOfficialFiscalProfileResponse;
import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;
import com.restaurante.fiscal.official.service.OfficialFiscalSubmissionService;
import com.restaurante.model.entity.OfficialFiscalSubmission;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant/fiscal/official")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Fiscal Official (Prep)", description = "Preparação para faturação eletrónica (AGT-ready). Desativado por padrão.")
public class TenantOfficialFiscalController {

    private final TenantGuard tenantGuard;
    private final OfficialFiscalSubmissionService service;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<TenantOfficialFiscalProfileResponse>> getProfile() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantOfficialFiscalProfile p = service.getProfile(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Official fiscal profile", p != null ? map(p) : null));
    }

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<TenantOfficialFiscalProfileResponse>> upsertProfile(@Valid @RequestBody UpsertTenantOfficialFiscalProfileRequest request,
                                                                                          HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantOfficialFiscalProfile p = service.upsertProfile(ctx.tenantId(), request);
        return ResponseEntity.ok(ApiResponse.success("Official fiscal profile atualizado", map(p)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<TenantOfficialFiscalProfileResponse>> updateProfile(@Valid @RequestBody UpsertTenantOfficialFiscalProfileRequest request,
                                                                                          HttpServletRequest http) {
        return upsertProfile(request, http);
    }

    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<Page<OfficialFiscalSubmissionResponse>>> listSubmissions(
            @RequestParam(name = "status", required = false) OfficialFiscalSubmissionStatus status,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<OfficialFiscalSubmission> page = service.listSubmissions(ctx.tenantId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Official submissions", page.map(this::map)));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> getSubmission(@PathVariable Long submissionId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.getSubmission(ctx.tenantId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Official submission", s != null ? map(s) : null));
    }

    @PostMapping("/submissions/create-for-document/{documentId}")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> createForDocument(@PathVariable Long documentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.createForDocument(ctx.tenantId(), documentId);
        return ResponseEntity.ok(ApiResponse.success("Official submission criada", map(s)));
    }

    @PostMapping("/submissions/{submissionId}/cancel")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> cancel(@PathVariable Long submissionId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.cancel(ctx.tenantId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Official submission cancelada", map(s)));
    }

    @PostMapping("/submissions/{submissionId}/retry")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> retry(@PathVariable Long submissionId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.retry(ctx.tenantId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Official submission marcada para retry", map(s)));
    }

    @PostMapping("/submissions/{submissionId}/simulate-submit")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> simulateSubmit(@PathVariable Long submissionId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.simulateSubmit(ctx.tenantId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Simulated submit", map(s)));
    }

    @PostMapping("/submissions/{submissionId}/simulate-accept")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> simulateAccept(@PathVariable Long submissionId,
                                                                                        @RequestBody(required = false) OfficialFiscalSimulationDecisionRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.simulateAccept(ctx.tenantId(), submissionId,
                request != null ? request.getCode() : null,
                request != null ? request.getMessage() : null);
        return ResponseEntity.ok(ApiResponse.success("Simulated accept", map(s)));
    }

    @PostMapping("/submissions/{submissionId}/simulate-reject")
    public ResponseEntity<ApiResponse<OfficialFiscalSubmissionResponse>> simulateReject(@PathVariable Long submissionId,
                                                                                        @RequestBody(required = false) OfficialFiscalSimulationDecisionRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalSubmission s = service.simulateReject(ctx.tenantId(), submissionId,
                request != null ? request.getCode() : null,
                request != null ? request.getMessage() : null);
        return ResponseEntity.ok(ApiResponse.success("Simulated reject", map(s)));
    }

    @GetMapping("/submissions/payload-preview")
    public ResponseEntity<ApiResponse<OfficialFiscalDocumentPayload>> payloadPreview(@RequestParam("documentId") Long documentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        OfficialFiscalDocumentPayload payload = service.payloadPreview(ctx.tenantId(), documentId);
        return ResponseEntity.ok(ApiResponse.success("Payload preview", payload));
    }

    private TenantOfficialFiscalProfileResponse map(TenantOfficialFiscalProfile p) {
        TenantOfficialFiscalProfileResponse r = new TenantOfficialFiscalProfileResponse();
        r.setId(p.getId());
        r.setTenantId(p.getTenant() != null ? p.getTenant().getId() : null);
        r.setStatus(p.getStatus());
        r.setCountryCode(p.getCountryCode());
        r.setAuthority(p.getAuthority());
        r.setOfficialEnabled(p.isOfficialEnabled());
        r.setEnvironment(p.getEnvironment());
        r.setSubmissionMode(p.getSubmissionMode());
        r.setTaxpayerNumber(p.getTaxpayerNumber());
        r.setSoftwareCertificateId(p.getSoftwareCertificateId());
        r.setSoftwareName(p.getSoftwareName());
        r.setSoftwareVersion(p.getSoftwareVersion());
        r.setProducerRegistrationId(p.getProducerRegistrationId());
        r.setPublicKeyId(p.getPublicKeyId());
        r.setTaxpayerKeyId(p.getTaxpayerKeyId());
        r.setSigningProfileId(p.getSigningProfile() != null ? p.getSigningProfile().getId() : null);
        r.setCallbackUrl(p.getCallbackUrl());
        return r;
    }

    private OfficialFiscalSubmissionResponse map(OfficialFiscalSubmission s) {
        OfficialFiscalSubmissionResponse r = new OfficialFiscalSubmissionResponse();
        r.setId(s.getId());
        r.setTenantId(s.getTenant() != null ? s.getTenant().getId() : null);
        r.setFiscalDocumentId(s.getFiscalDocument() != null ? s.getFiscalDocument().getId() : null);
        r.setOriginalFiscalDocumentId(s.getOriginalFiscalDocument() != null ? s.getOriginalFiscalDocument().getId() : null);
        r.setDocumentType(s.getDocumentType() != null ? s.getDocumentType().name() : null);
        r.setStatus(s.getStatus());
        r.setAuthority(s.getAuthority());
        r.setEnvironment(s.getEnvironment());
        r.setRequestId(s.getRequestId());
        r.setOfficialDocumentId(s.getOfficialDocumentId());
        r.setOfficialStatusCode(s.getOfficialStatusCode());
        r.setOfficialStatusMessage(s.getOfficialStatusMessage());
        r.setPayloadHash(s.getPayloadHash());
        r.setSignedPayloadHash(s.getSignedPayloadHash());
        r.setSubmittedAt(s.getSubmittedAt());
        r.setAcceptedAt(s.getAcceptedAt());
        r.setRejectedAt(s.getRejectedAt());
        r.setAttemptCount(s.getAttemptCount());
        r.setMaxAttempts(s.getMaxAttempts());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
