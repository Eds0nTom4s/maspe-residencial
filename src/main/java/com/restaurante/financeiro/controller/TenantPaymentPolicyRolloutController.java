package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.PaymentPolicyRolloutRerunRequest;
import com.restaurante.dto.response.*;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyRollout;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyRolloutItem;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyAsyncRolloutService;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutItemStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant/payment-policy-rollouts")
@RequiredArgsConstructor
@Tag(name = "Payment Policy Rollouts", description = "Status/itens e rerun de rollouts de templates (tenant-aware)")
public class TenantPaymentPolicyRolloutController {

    private final TenantGuard tenantGuard;
    private final PaymentMethodPolicyAsyncRolloutService asyncRolloutService;

    @GetMapping("/{rolloutId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyRolloutStatusResponse>> get(@PathVariable Long rolloutId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        PaymentMethodPolicyRollout r = asyncRolloutService.getRollout(rolloutId);
        return ResponseEntity.ok(ApiResponse.success("Rollout", toStatus(r)));
    }

    @GetMapping("/{rolloutId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PaymentPolicyRolloutItemResponse>>> items(
            @PathVariable Long rolloutId,
            @RequestParam(required = false) PaymentMethodPolicyRolloutItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        Page<PaymentMethodPolicyRolloutItem> p = asyncRolloutService.pageItems(rolloutId, status, page, size);
        Page<PaymentPolicyRolloutItemResponse> mapped = p.map(this::toItem);
        return ResponseEntity.ok(ApiResponse.success("Itens do rollout", mapped));
    }

    @PostMapping("/{rolloutId}/rerun")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyRolloutStatusResponse>> rerun(
            @PathVariable Long rolloutId,
            @Valid @RequestBody(required = false) PaymentPolicyRolloutRerunRequest req,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentMethodPolicyRollout r = asyncRolloutService.rerun(rolloutId, req != null ? req.getOnlyStatuses() : null, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Rerun solicitado", toStatus(r)));
    }

    private PaymentPolicyRolloutStatusResponse toStatus(PaymentMethodPolicyRollout r) {
        PaymentPolicyRolloutStatusResponse s = new PaymentPolicyRolloutStatusResponse();
        s.setRolloutId(r.getId());
        s.setTemplateId(r.getTemplate() != null ? r.getTemplate().getId() : null);
        s.setUnidadeId(r.getUnidadeAtendimento() != null ? r.getUnidadeAtendimento().getId() : null);
        s.setStatus(r.getStatus());
        s.setExecutionMode(r.getExecutionMode());
        s.setRolloutMode(r.getRolloutMode());
        s.setOverwriteMode(r.getOverwriteMode());
        s.setTotalItems(r.getTotalItems());
        s.setProcessedItems(r.getProcessedItems());
        s.setPendingItems(r.getPendingItems());
        s.setSucceededItems(r.getSucceededItems());
        s.setSkippedItems(r.getSkippedItems());
        s.setFailedItems(r.getFailedItems());
        s.setRetryCount(r.getRetryCount());
        s.setRequestedAt(r.getRequestedAt());
        s.setStartedAt(r.getStartedAt());
        s.setFinishedAt(r.getFinishedAt());
        s.setLastProgressAt(r.getLastProgressAt());
        s.setLastError(r.getLastError());
        if (r.getTotalItems() > 0) {
            s.setProgressPercent((int) Math.floor((r.getProcessedItems() * 100.0) / r.getTotalItems()));
        } else {
            s.setProgressPercent(0);
        }
        return s;
    }

    private PaymentPolicyRolloutItemResponse toItem(PaymentMethodPolicyRolloutItem i) {
        PaymentPolicyRolloutItemResponse r = new PaymentPolicyRolloutItemResponse();
        r.setId(i.getId());
        r.setRolloutId(i.getRollout() != null ? i.getRollout().getId() : null);
        r.setDeviceId(i.getDispositivoOperacional() != null ? i.getDispositivoOperacional().getId() : null);
        r.setPaymentMethodCode(i.getPaymentMethodCode());
        r.setPlannedAction(i.getPlannedAction());
        r.setStatus(i.getStatus());
        r.setOverwriteMode(i.getOverwriteMode());
        r.setManualOverrideDetected(i.isManualOverrideDetected());
        r.setSkippedReason(i.getSkippedReason());
        r.setErrorCode(i.getErrorCode());
        r.setErrorMessage(i.getErrorMessage());
        r.setAttempts(i.getAttempts());
        r.setCreatedAt(i.getCreatedAt());
        r.setStartedAt(i.getStartedAt());
        r.setFinishedAt(i.getFinishedAt());
        r.setUpdatedAt(i.getUpdatedAt());
        return r;
    }
}

