package com.restaurante.controller;

import com.restaurante.billing.dto.request.UpsertTenantBillingCollectionPolicyRequest;
import com.restaurante.billing.dto.response.TenantBillingCollectionStatusResponse;
import com.restaurante.billing.dto.response.TenantBillingPaymentResponse;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantBillingPaymentRepository;
import com.restaurante.billing.service.TenantAccessBillingGuardService;
import com.restaurante.billing.service.TenantBillingCollectionPolicyService;
import com.restaurante.billing.service.TenantBillingCollectionService;
import com.restaurante.billing.service.TenantBillingPaymentService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingPayment;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;

@RestController
@RequestMapping("/admin/billing")
@RequiredArgsConstructor
@Tag(name = "Admin Billing Payments", description = "Cobrança SaaS (PLATFORM_ADMIN)")
public class AdminBillingPaymentController {

    private final TenantGuard tenantGuard;
    private final TenantBillingPaymentRepository paymentRepository;
    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingPaymentService paymentService;
    private final TenantBillingCollectionService collectionService;
    private final TenantBillingCollectionPolicyService policyService;
    private final TenantAccessBillingGuardService guardService;

    @GetMapping("/payments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantBillingPaymentResponse>>> listPayments() {
        tenantGuard.assertPlatformAdmin();
        // MVP: list last 200 across all tenants
        List<TenantBillingPaymentResponse> out = paymentRepository.findAll(PageRequest.of(0, 200)).stream()
                .map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Payments", out));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingPaymentResponse>> getPayment(@PathVariable Long paymentId) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingPayment p = paymentRepository.findById(paymentId).orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("Payment", map(p)));
    }

    @PostMapping("/payments/{paymentId}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingPaymentResponse>> confirm(@PathVariable Long paymentId) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingPayment p = paymentRepository.findById(paymentId).orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        TenantBillingPayment confirmed = paymentService.confirmPayment(p.getTenant() != null ? p.getTenant().getId() : null, p.getId());
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", map(confirmed)));
    }

    @PostMapping("/payments/{paymentId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingPaymentResponse>> reject(@PathVariable Long paymentId, @RequestBody(required = false) java.util.Map<String, String> body) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingPayment p = paymentRepository.findById(paymentId).orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        String reason = body != null ? body.get("reason") : null;
        TenantBillingPayment rejected = paymentService.rejectPayment(p.getTenant() != null ? p.getTenant().getId() : null, p.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Payment rejected", map(rejected)));
    }

    @PostMapping("/payments/{paymentId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingPaymentResponse>> cancel(@PathVariable Long paymentId, @RequestBody(required = false) java.util.Map<String, String> body) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingPayment p = paymentRepository.findById(paymentId).orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        String reason = body != null ? body.get("reason") : null;
        TenantBillingPayment cancelled = paymentService.cancelPayment(p.getTenant() != null ? p.getTenant().getId() : null, p.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled", map(cancelled)));
    }

    @PostMapping("/invoices/{invoiceId}/mark-overdue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> markOverdue(@PathVariable Long invoiceId) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingInvoice inv = invoiceRepository.findById(invoiceId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        collectionService.markInvoiceOverdue(inv.getTenant() != null ? inv.getTenant().getId() : null, inv.getId());
        return ResponseEntity.ok(ApiResponse.success("Invoice marked overdue", inv.getId()));
    }

    @PostMapping("/tenants/{tenantId}/evaluate-collection")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingCollectionStatusResponse>> evaluate(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingCollectionStatusResponse r = computeCollection(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Collection evaluated", r));
    }

    @GetMapping("/tenants/{tenantId}/collection-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingCollectionStatusResponse>> status(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingCollectionStatusResponse r = computeCollection(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Collection status", r));
    }

    @GetMapping("/tenants/{tenantId}/collection-policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingCollectionPolicy>> getPolicy(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Policy", policyService.get(tenantId)));
    }

    @PutMapping("/tenants/{tenantId}/collection-policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantBillingCollectionPolicy>> putPolicy(@PathVariable Long tenantId, @Valid @RequestBody UpsertTenantBillingCollectionPolicyRequest req) {
        tenantGuard.assertPlatformAdmin();
        TenantBillingCollectionPolicy saved = policyService.upsert(tenantId, p -> {
            if (req.getGracePeriodDays() != null) p.setGracePeriodDays(req.getGracePeriodDays());
            if (req.getOverdueWarningDays() != null) p.setOverdueWarningDays(req.getOverdueWarningDays());
            if (req.getAutoMarkOverdue() != null) p.setAutoMarkOverdue(req.getAutoMarkOverdue());
            if (req.getAllowOperationWhenOverdue() != null) p.setAllowOperationWhenOverdue(req.getAllowOperationWhenOverdue());
            if (req.getAllowOperationWhenSuspended() != null) p.setAllowOperationWhenSuspended(req.getAllowOperationWhenSuspended());
            if (req.getSuspensionMode() != null) p.setSuspensionMode(req.getSuspensionMode());
            if (req.getSuspensionAfterDays() != null) p.setSuspensionAfterDays(req.getSuspensionAfterDays());
            if (req.getRestrictNewOrders() != null) p.setRestrictNewOrders(req.getRestrictNewOrders());
            if (req.getRestrictNewDevices() != null) p.setRestrictNewDevices(req.getRestrictNewDevices());
            if (req.getRestrictAdminAccess() != null) p.setRestrictAdminAccess(req.getRestrictAdminAccess());
            if (req.getStatus() != null) p.setStatus(req.getStatus());
        });
        return ResponseEntity.ok(ApiResponse.success("Policy saved", saved));
    }

    private TenantBillingCollectionStatusResponse computeCollection(Long tenantId) {
        var st = collectionService.evaluateTenantBillingStatus(tenantId, LocalDateTime.now());
        var pol = collectionService.getEffectivePolicy(tenantId);

        TenantBillingCollectionStatusResponse r = new TenantBillingCollectionStatusResponse();
        r.setTenantId(tenantId);
        r.setCollectionStatus(st);
        r.setSuspensionMode(pol.getSuspensionMode());
        r.setRestrictNewOrders(pol.isRestrictNewOrders());
        r.setRestrictNewDevices(pol.isRestrictNewDevices());
        r.setRestrictAdminAccess(pol.isRestrictAdminAccess());
        r.setMessageCode(guardService.evaluateAccess(tenantId, com.restaurante.billing.enums.TenantBillingOperationType.ADD_DEVICE).getMessageCode());

        List<TenantBillingInvoice> invoices = invoiceRepository.findByTenantIdOrderByIdDesc(tenantId);
        BigDecimal outstanding = BigDecimal.ZERO;
        int overdueCount = 0;
        for (TenantBillingInvoice i : invoices) {
            if (i == null) continue;
            if (i.getStatus() == TenantBillingInvoiceStatus.OVERDUE) overdueCount++;
            outstanding = outstanding.add(nz(i.getOutstandingAmount()));
        }
        r.setOverdueInvoices(overdueCount);
        r.setTotalOutstandingAmount(scaleMoney(outstanding));

        // gracePeriodEndsAt: take the nearest grace end among overdue invoices
        LocalDateTime grace = null;
        for (TenantBillingInvoice i : invoices) {
            if (i == null) continue;
            if (i.getGracePeriodEndsAt() == null) continue;
            if (grace == null || i.getGracePeriodEndsAt().isBefore(grace)) grace = i.getGracePeriodEndsAt();
        }
        r.setGracePeriodEndsAt(grace);
        return r;
    }

    private TenantBillingPaymentResponse map(TenantBillingPayment p) {
        TenantBillingPaymentResponse r = new TenantBillingPaymentResponse();
        r.setId(p.getId());
        r.setTenantId(p.getTenant() != null ? p.getTenant().getId() : null);
        r.setInvoiceId(p.getInvoice() != null ? p.getInvoice().getId() : null);
        r.setBillingCycleId(p.getBillingCycle() != null ? p.getBillingCycle().getId() : null);
        r.setSubscriptionId(p.getSubscription() != null ? p.getSubscription().getId() : null);
        r.setPaymentNumber(p.getPaymentNumber());
        r.setStatus(p.getStatus());
        r.setPaymentMethod(p.getPaymentMethod());
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setPaidAt(p.getPaidAt());
        r.setReceivedAt(p.getReceivedAt());
        r.setConfirmedAt(p.getConfirmedAt());
        r.setReference(p.getReference());
        r.setProofReference(p.getProofReference());
        r.setNotes(p.getNotes());
        return r;
    }
}

