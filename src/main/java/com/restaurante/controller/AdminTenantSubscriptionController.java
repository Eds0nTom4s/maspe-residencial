package com.restaurante.controller;

import com.restaurante.billing.dto.request.UpsertTenantSubscriptionRequest;
import com.restaurante.billing.dto.response.TenantSubscriptionResponse;
import com.restaurante.billing.service.TenantSubscriptionService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants/{tenantId}/billing/subscription")
@RequiredArgsConstructor
@Tag(name = "Admin Tenant Billing", description = "Gestão de subscription do tenant (PLATFORM_ADMIN)")
public class AdminTenantSubscriptionController {

    private final TenantGuard tenantGuard;
    private final TenantSubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponse>> get(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        TenantSubscription sub = subscriptionService.getCurrentForTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Subscription", sub != null ? map(sub) : null));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponse>> create(@PathVariable Long tenantId, @Valid @RequestBody UpsertTenantSubscriptionRequest req) {
        tenantGuard.assertPlatformAdmin();
        TenantSubscription sub = subscriptionService.createOrReplaceForTenant(tenantId, req.getBillingPlanId(), req.getStatus(), req.getBillingAnchorDay());
        return ResponseEntity.ok(ApiResponse.success("Subscription criada", map(sub)));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponse>> update(@PathVariable Long tenantId, @Valid @RequestBody UpsertTenantSubscriptionRequest req) {
        tenantGuard.assertPlatformAdmin();
        TenantSubscription current = subscriptionService.getCurrentForTenant(tenantId);
        if (current == null) {
            TenantSubscription sub = subscriptionService.createOrReplaceForTenant(tenantId, req.getBillingPlanId(), req.getStatus(), req.getBillingAnchorDay());
            return ResponseEntity.ok(ApiResponse.success("Subscription criada", map(sub)));
        }
        TenantSubscription sub = subscriptionService.updateForTenant(tenantId, current.getId(), req.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Subscription atualizada", map(sub)));
    }

    private TenantSubscriptionResponse map(TenantSubscription s) {
        TenantSubscriptionResponse r = new TenantSubscriptionResponse();
        r.setId(s.getId());
        r.setTenantId(s.getTenant() != null ? s.getTenant().getId() : null);
        r.setBillingPlanId(s.getBillingPlan() != null ? s.getBillingPlan().getId() : null);
        r.setStatus(s.getStatus());
        r.setStartedAt(s.getStartedAt());
        r.setCurrentPeriodStart(s.getCurrentPeriodStart());
        r.setCurrentPeriodEnd(s.getCurrentPeriodEnd());
        r.setBillingAnchorDay(s.getBillingAnchorDay());
        r.setCurrency(s.getCurrency());
        return r;
    }
}

