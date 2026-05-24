package com.restaurante.controller;

import com.restaurante.dto.request.AprovarDivergenciaCaixaOperadorRequest;
import com.restaurante.dto.request.RejeitarDivergenciaCaixaOperadorRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CaixaOperadorAdjustmentResponse;
import com.restaurante.dto.response.CaixaOperadorDivergenceResponse;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.divergence.service.CaixaOperadorDivergenceService;
import com.restaurante.model.entity.CaixaOperadorAdjustment;
import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/caixa-operador")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Caixa Operador Divergences", description = "Governança formal de divergências/ajustes de caixa operador/device (CASH/TPA)")
public class TenantCaixaOperadorDivergenceController {

    private final TenantGuard tenantGuard;
    private final CaixaOperadorDivergenceRepository divergenceRepository;
    private final CaixaOperadorAdjustmentRepository adjustmentRepository;
    private final CaixaOperadorDivergenceService divergenceService;

    @GetMapping("/divergences")
    public ResponseEntity<ApiResponse<Page<CaixaOperadorDivergenceResponse>>> listDivergences(
            @RequestParam(name = "status", required = false) CaixaOperadorDivergenceStatus status,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        Page<CaixaOperadorDivergence> page = divergenceRepository.searchByTenant(ctx.tenantId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Divergências", page.map(this::map)));
    }

    @GetMapping("/divergences/{divergenceId}")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> getDivergence(@PathVariable Long divergenceId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        CaixaOperadorDivergence d = divergenceRepository.findById(divergenceId).orElse(null);
        if (d == null || d.getTenant() == null || !d.getTenant().getId().equals(ctx.tenantId())) {
            return ResponseEntity.ok(ApiResponse.success("Divergência", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Divergência", map(d)));
    }

    @GetMapping("/{caixaId}/divergences")
    public ResponseEntity<ApiResponse<List<CaixaOperadorDivergenceResponse>>> listByCaixa(@PathVariable Long caixaId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        List<CaixaOperadorDivergence> list = divergenceRepository.findByTenantIdAndCaixaOperadorSessionId(ctx.tenantId(), caixaId);
        return ResponseEntity.ok(ApiResponse.success("Divergências", list.stream().map(this::map).toList()));
    }

    @PostMapping("/divergences/{divergenceId}/approve")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> approve(@PathVariable Long divergenceId,
                                                                                @Valid @RequestBody AprovarDivergenciaCaixaOperadorRequest request) {
        CaixaOperadorDivergence d = divergenceService.approveByTenant(
                divergenceId,
                request.getReviewNotes(),
                request.getAdjustmentType(),
                request.getDirection(),
                request.getEvidenceReference()
        );
        return ResponseEntity.ok(ApiResponse.success("Divergência aprovada", map(d)));
    }

    @PostMapping("/divergences/{divergenceId}/reject")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> reject(@PathVariable Long divergenceId,
                                                                               @Valid @RequestBody RejeitarDivergenciaCaixaOperadorRequest request) {
        CaixaOperadorDivergence d = divergenceService.rejectByTenant(divergenceId, request.getReviewNotes());
        return ResponseEntity.ok(ApiResponse.success("Divergência rejeitada", map(d)));
    }

    @GetMapping("/adjustments")
    public ResponseEntity<ApiResponse<Page<CaixaOperadorAdjustmentResponse>>> listAdjustments(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        Page<CaixaOperadorAdjustment> page = adjustmentRepository.findByTenantId(ctx.tenantId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Ajustes", page.map(this::mapAdj)));
    }

    private CaixaOperadorDivergenceResponse map(CaixaOperadorDivergence d) {
        CaixaOperadorDivergenceResponse r = new CaixaOperadorDivergenceResponse();
        r.setId(d.getId());
        r.setTenantId(d.getTenant() != null ? d.getTenant().getId() : null);
        r.setUnidadeAtendimentoId(d.getUnidadeAtendimento() != null ? d.getUnidadeAtendimento().getId() : null);
        r.setTurnoOperacionalId(d.getTurnoOperacional() != null ? d.getTurnoOperacional().getId() : null);
        r.setCaixaOperadorSessionId(d.getCaixaOperadorSession() != null ? d.getCaixaOperadorSession().getId() : null);
        r.setDeviceId(d.getDispositivoOperacional() != null ? d.getDispositivoOperacional().getId() : null);
        r.setOperadorUserId(d.getOperador() != null ? d.getOperador().getId() : null);
        r.setStatus(d.getStatus());
        r.setType(d.getType());
        r.setSeverity(d.getSeverity());
        r.setPaymentMethod(d.getPaymentMethod());
        r.setExpectedAmount(d.getExpectedAmount());
        r.setDeclaredAmount(d.getDeclaredAmount());
        r.setDifferenceAmount(d.getDifferenceAmount());
        r.setAbsoluteDifferenceAmount(d.getAbsoluteDifferenceAmount());
        r.setReasonCategory(d.getReasonCategory());
        r.setDescription(d.getDescription());
        r.setSubmittedByUserId(d.getSubmittedBy() != null ? d.getSubmittedBy().getId() : null);
        r.setSubmittedAt(d.getSubmittedAt());
        r.setReviewedByUserId(d.getReviewedBy() != null ? d.getReviewedBy().getId() : null);
        r.setReviewedAt(d.getReviewedAt());
        r.setReviewNotes(d.getReviewNotes());
        return r;
    }

    private CaixaOperadorAdjustmentResponse mapAdj(CaixaOperadorAdjustment a) {
        CaixaOperadorAdjustmentResponse r = new CaixaOperadorAdjustmentResponse();
        r.setId(a.getId());
        r.setTenantId(a.getTenant() != null ? a.getTenant().getId() : null);
        r.setDivergenceId(a.getDivergence() != null ? a.getDivergence().getId() : null);
        r.setCaixaOperadorSessionId(a.getCaixaOperadorSession() != null ? a.getCaixaOperadorSession().getId() : null);
        r.setAdjustmentType(a.getAdjustmentType());
        r.setPaymentMethod(a.getPaymentMethod());
        r.setAmount(a.getAmount());
        r.setDirection(a.getDirection());
        r.setStatus(a.getStatus());
        r.setApprovedByUserId(a.getApprovedBy() != null ? a.getApprovedBy().getId() : null);
        r.setApprovedAt(a.getApprovedAt());
        r.setReason(a.getReason());
        r.setEvidenceReference(a.getEvidenceReference());
        return r;
    }
}

