package com.restaurante.controller;

import com.restaurante.billing.dto.request.CreateUsageAdjustmentRequest;
import com.restaurante.billing.dto.response.BillingCycleResponse;
import com.restaurante.billing.dto.response.TenantBillingInvoiceLineResponse;
import com.restaurante.billing.dto.response.TenantBillingInvoiceResponse;
import com.restaurante.billing.dto.response.TenantSubscriptionResponse;
import com.restaurante.billing.dto.response.UsageAggregationResponse;
import com.restaurante.billing.dto.response.UsageEventResponse;
import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceLineRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.UsageAggregationRepository;
import com.restaurante.billing.repository.UsageEventRepository;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.billing.service.TenantBillingInvoiceService;
import com.restaurante.billing.service.TenantSubscriptionService;
import com.restaurante.billing.service.UsageAdjustmentService;
import com.restaurante.billing.service.UsageAggregationService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingInvoiceLine;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.UsageAggregationStatus;
import com.restaurante.model.enums.UsageMetricCode;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/billing")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Billing", description = "Billing SaaS (tenant finance/admin)")
public class TenantBillingController {

    private final TenantGuard tenantGuard;
    private final TenantSubscriptionService subscriptionService;
    private final BillingCycleService cycleService;
    private final BillingCycleRepository cycleRepository;
    private final UsageEventRepository usageEventRepository;
    private final UsageAggregationService aggregationService;
    private final UsageAggregationRepository aggregationRepository;
    private final TenantBillingInvoiceService invoiceService;
    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingInvoiceLineRepository invoiceLineRepository;
    private final UsageAdjustmentService adjustmentService;

    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponse>> subscription() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantSubscription sub = subscriptionService.getCurrentForTenant(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Subscription", sub != null ? map(sub) : null));
    }

    @GetMapping("/usage/events")
    public ResponseEntity<ApiResponse<List<UsageEventResponse>>> usageEvents() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<UsageEventResponse> out = usageEventRepository.findByTenantIdOrderByOccurredAtDesc(ctx.tenantId())
                .stream().limit(500).map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Usage events", out));
    }

    @GetMapping("/usage/aggregations")
    public ResponseEntity<ApiResponse<List<UsageAggregationResponse>>> aggregations() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<UsageAggregationResponse> out = aggregationRepository.findByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), UsageAggregationStatus.DRAFT)
                .stream().limit(100).map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Usage aggregations", out));
    }

    @PostMapping("/usage/aggregate-current-cycle")
    public ResponseEntity<ApiResponse<List<UsageAggregationResponse>>> aggregateCurrentCycle() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantSubscription sub = subscriptionService.getCurrentForTenant(ctx.tenantId());
        if (sub == null) throw new BusinessException("TENANT_SUBSCRIPTION_NOT_FOUND");
        BillingCycle cycle = cycleService.getOrOpenCurrentCycle(sub, null);
        UsageAggregation a = aggregationService.aggregateForPeriod(ctx.tenantId(), sub, UsageMetricCode.PAYMENT_CONFIRMED, cycle.getPeriodStart(), cycle.getPeriodEnd());
        a.setBillingCycle(cycle);
        a = aggregationRepository.save(a);
        return ResponseEntity.ok(ApiResponse.success("Aggregated", List.of(map(a))));
    }

    @GetMapping("/cycles")
    public ResponseEntity<ApiResponse<Page<BillingCycleResponse>>> cycles(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<BillingCycleResponse> out = cycleRepository.findByTenantIdOrderByPeriodStartDesc(ctx.tenantId(), pageable).map(this::map);
        return ResponseEntity.ok(ApiResponse.success("Cycles", out));
    }

    @GetMapping("/cycles/{cycleId}")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> cycle(@PathVariable Long cycleId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        BillingCycle c = cycleRepository.findByTenantIdAndId(ctx.tenantId(), cycleId).orElseThrow(() -> new BusinessException("BILLING_CYCLE_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("Cycle", map(c)));
    }

    @PostMapping("/cycles/{cycleId}/finalize-usage")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> finalizeUsage(@PathVariable Long cycleId) {
        var ctx = tenantGuard.requireContext();
        BillingCycle c = cycleService.finalizeUsage(ctx.tenantId(), cycleId);
        return ResponseEntity.ok(ApiResponse.success("Cycle finalized", map(c)));
    }

    @PostMapping("/cycles/{cycleId}/generate-invoice")
    public ResponseEntity<ApiResponse<TenantBillingInvoiceResponse>> generateInvoice(@PathVariable Long cycleId) {
        var ctx = tenantGuard.requireContext();
        BillingCycle c = cycleRepository.findByTenantIdAndId(ctx.tenantId(), cycleId).orElseThrow(() -> new BusinessException("BILLING_CYCLE_NOT_FOUND"));
        TenantBillingInvoice inv = invoiceService.generateForCycle(ctx.tenantId(), c);
        inv = invoiceService.issue(ctx.tenantId(), inv.getId());
        return ResponseEntity.ok(ApiResponse.success("Invoice generated", map(inv)));
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<TenantBillingInvoiceResponse>>> invoices() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<TenantBillingInvoiceResponse> out = invoiceRepository.findByTenantIdOrderByIdDesc(ctx.tenantId()).stream().limit(50).map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Invoices", out));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<ApiResponse<TenantBillingInvoiceResponse>> invoice(@PathVariable Long invoiceId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(ctx.tenantId(), invoiceId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("Invoice", map(inv)));
    }

    @PostMapping("/invoices/{invoiceId}/mark-paid")
    public ResponseEntity<ApiResponse<TenantBillingInvoiceResponse>> markPaid(@PathVariable Long invoiceId) {
        var ctx = tenantGuard.requireContext();
        TenantBillingInvoice inv = invoiceService.markPaid(ctx.tenantId(), invoiceId);
        return ResponseEntity.ok(ApiResponse.success("Invoice paid", map(inv)));
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    public ResponseEntity<ApiResponse<TenantBillingInvoiceResponse>> cancel(@PathVariable Long invoiceId, @RequestBody(required = false) java.util.Map<String, String> body) {
        var ctx = tenantGuard.requireContext();
        String reason = body != null ? body.get("reason") : null;
        TenantBillingInvoice inv = invoiceService.cancel(ctx.tenantId(), invoiceId, reason);
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled", map(inv)));
    }

    @PostMapping("/usage-adjustments")
    public ResponseEntity<ApiResponse<Long>> createAdjustment(@Valid @RequestBody CreateUsageAdjustmentRequest req) {
        var ctx = tenantGuard.requireContext();
        var adj = adjustmentService.create(
                ctx.tenantId(),
                req.getMetricCode(),
                req.getAdjustmentType(),
                req.getQuantityDelta(),
                req.getAmountDelta(),
                req.getReason(),
                req.getReferenceType(),
                req.getReferenceId(),
                null
        );
        return ResponseEntity.ok(ApiResponse.success("Adjustment created", adj.getId()));
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

    private UsageEventResponse map(UsageEvent e) {
        UsageEventResponse r = new UsageEventResponse();
        r.setId(e.getId());
        r.setTenantId(e.getTenant() != null ? e.getTenant().getId() : null);
        r.setMetricCode(e.getMetricCode());
        r.setSourceEventType(e.getSourceEventType());
        r.setSourceEntityType(e.getSourceEntityType());
        r.setSourceEntityId(e.getSourceEntityId());
        r.setIdempotencyKey(e.getIdempotencyKey());
        r.setOccurredAt(e.getOccurredAt());
        r.setQuantity(e.getQuantity());
        r.setAmount(e.getAmount());
        r.setCurrency(e.getCurrency());
        r.setStatus(e.getStatus());
        return r;
    }

    private UsageAggregationResponse map(UsageAggregation a) {
        UsageAggregationResponse r = new UsageAggregationResponse();
        r.setId(a.getId());
        r.setTenantId(a.getTenant() != null ? a.getTenant().getId() : null);
        r.setSubscriptionId(a.getSubscription() != null ? a.getSubscription().getId() : null);
        r.setBillingCycleId(a.getBillingCycle() != null ? a.getBillingCycle().getId() : null);
        r.setMetricCode(a.getMetricCode());
        r.setPeriodStart(a.getPeriodStart());
        r.setPeriodEnd(a.getPeriodEnd());
        r.setQuantityTotal(a.getQuantityTotal());
        r.setAmountTotal(a.getAmountTotal());
        r.setBillableQuantity(a.getBillableQuantity());
        r.setIncludedQuantity(a.getIncludedQuantity());
        r.setOverageQuantity(a.getOverageQuantity());
        r.setCalculatedChargeAmount(a.getCalculatedChargeAmount());
        r.setCurrency(a.getCurrency());
        r.setStatus(a.getStatus());
        return r;
    }

    private BillingCycleResponse map(BillingCycle c) {
        BillingCycleResponse r = new BillingCycleResponse();
        r.setId(c.getId());
        r.setTenantId(c.getTenant() != null ? c.getTenant().getId() : null);
        r.setSubscriptionId(c.getSubscription() != null ? c.getSubscription().getId() : null);
        r.setPeriodStart(c.getPeriodStart());
        r.setPeriodEnd(c.getPeriodEnd());
        r.setStatus(c.getStatus());
        r.setUsageFinalizedAt(c.getUsageFinalizedAt());
        r.setInvoiceGeneratedAt(c.getInvoiceGeneratedAt());
        return r;
    }

    private TenantBillingInvoiceResponse map(TenantBillingInvoice inv) {
        TenantBillingInvoiceResponse r = new TenantBillingInvoiceResponse();
        r.setId(inv.getId());
        r.setTenantId(inv.getTenant() != null ? inv.getTenant().getId() : null);
        r.setSubscriptionId(inv.getSubscription() != null ? inv.getSubscription().getId() : null);
        r.setBillingCycleId(inv.getBillingCycle() != null ? inv.getBillingCycle().getId() : null);
        r.setInvoiceNumber(inv.getInvoiceNumber());
        r.setStatus(inv.getStatus());
        r.setCurrency(inv.getCurrency());
        r.setSubtotalAmount(inv.getSubtotalAmount());
        r.setDiscountAmount(inv.getDiscountAmount());
        r.setTaxAmount(inv.getTaxAmount());
        r.setTotalAmount(inv.getTotalAmount());
        r.setIssuedAt(inv.getIssuedAt());
        r.setDueAt(inv.getDueAt());
        r.setPaidAt(inv.getPaidAt());
        r.setCancelledAt(inv.getCancelledAt());
        r.setNotes(inv.getNotes());

        List<TenantBillingInvoiceLine> lines = invoiceLineRepository.findByTenantIdAndInvoice_IdOrderByIdAsc(r.getTenantId(), inv.getId());
        r.setLines(lines.stream().map(this::mapLine).toList());
        return r;
    }

    private TenantBillingInvoiceLineResponse mapLine(TenantBillingInvoiceLine l) {
        TenantBillingInvoiceLineResponse r = new TenantBillingInvoiceLineResponse();
        r.setId(l.getId());
        r.setMetricCode(l.getMetricCode());
        r.setDescription(l.getDescription());
        r.setQuantity(l.getQuantity());
        r.setUnitPrice(l.getUnitPrice());
        r.setAmount(l.getAmount());
        r.setIncludedQuantity(l.getIncludedQuantity());
        r.setOverageQuantity(l.getOverageQuantity());
        r.setPeriodStart(l.getPeriodStart());
        r.setPeriodEnd(l.getPeriodEnd());
        return r;
    }
}
