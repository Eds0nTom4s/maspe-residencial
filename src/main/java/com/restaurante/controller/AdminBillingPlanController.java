package com.restaurante.controller;

import com.restaurante.billing.dto.request.CreateBillingPlanRequest;
import com.restaurante.billing.dto.request.UpdateBillingPlanRequest;
import com.restaurante.billing.dto.response.BillingPlanResponse;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.service.BillingPlanService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.entity.BillingPlan;
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

import java.util.List;

@RestController
@RequestMapping("/admin/billing/plans")
@RequiredArgsConstructor
@Tag(name = "Admin Billing Plans", description = "Gestão de planos (PLATFORM_ADMIN)")
public class AdminBillingPlanController {

    private final TenantGuard tenantGuard;
    private final BillingPlanService planService;
    private final BillingPlanRepository planRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BillingPlanResponse>>> list() {
        tenantGuard.assertPlatformAdmin();
        List<BillingPlanResponse> out = planRepository.findAll().stream().map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Plans", out));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BillingPlanResponse>> create(@Valid @RequestBody CreateBillingPlanRequest req) {
        tenantGuard.assertPlatformAdmin();
        BillingPlan plan = new BillingPlan();
        plan.setCode(req.getCode());
        plan.setName(req.getName());
        plan.setDescription(req.getDescription());
        plan.setStatus(req.getStatus());
        plan.setBillingInterval(req.getBillingInterval());
        plan.setCurrency(req.getCurrency());
        plan.setBasePrice(req.getBasePrice());
        plan.setIncludedTransactions(req.getIncludedTransactions());
        plan.setOveragePricePerTransaction(req.getOveragePricePerTransaction());
        plan.setTransactionFeePercentage(req.getTransactionFeePercentage());
        plan.setMinimumMonthlyFee(req.getMinimumMonthlyFee());
        BillingPlan saved = planService.create(plan);
        return ResponseEntity.ok(ApiResponse.success("Plan criado", map(saved)));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BillingPlanResponse>> update(@PathVariable Long planId, @Valid @RequestBody UpdateBillingPlanRequest req) {
        tenantGuard.assertPlatformAdmin();
        BillingPlan patch = new BillingPlan();
        patch.setName(req.getName());
        patch.setDescription(req.getDescription());
        patch.setStatus(req.getStatus());
        patch.setBillingInterval(req.getBillingInterval());
        patch.setCurrency(req.getCurrency());
        patch.setBasePrice(req.getBasePrice());
        patch.setIncludedTransactions(req.getIncludedTransactions());
        patch.setOveragePricePerTransaction(req.getOveragePricePerTransaction());
        patch.setTransactionFeePercentage(req.getTransactionFeePercentage());
        patch.setMinimumMonthlyFee(req.getMinimumMonthlyFee());
        BillingPlan saved = planService.update(planId, patch);
        return ResponseEntity.ok(ApiResponse.success("Plan atualizado", map(saved)));
    }

    private BillingPlanResponse map(BillingPlan p) {
        BillingPlanResponse r = new BillingPlanResponse();
        r.setId(p.getId());
        r.setCode(p.getCode());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setStatus(p.getStatus());
        r.setBillingInterval(p.getBillingInterval());
        r.setCurrency(p.getCurrency());
        r.setBasePrice(p.getBasePrice());
        r.setIncludedTransactions(p.getIncludedTransactions());
        r.setOveragePricePerTransaction(p.getOveragePricePerTransaction());
        r.setTransactionFeePercentage(p.getTransactionFeePercentage());
        r.setMinimumMonthlyFee(p.getMinimumMonthlyFee());
        return r;
    }
}

