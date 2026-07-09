package com.restaurante.billing.service;

import com.restaurante.billing.hash.TenantBillingInvoiceHashService;
import com.restaurante.billing.repository.TenantBillingInvoiceLineRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.UsageAggregationRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingInvoiceLine;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;
import static com.restaurante.billing.util.BillingMath.scaleQty;

@Service
@RequiredArgsConstructor
public class TenantBillingInvoiceService {

    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingInvoiceLineRepository lineRepository;
    private final UsageAggregationRepository aggregationRepository;
    private final TenantBillingInvoiceSequenceService sequenceService;
    private final TenantBillingInvoiceHashService hashService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public TenantBillingInvoice generateForCycle(Long tenantId, BillingCycle cycle) {
        if (tenantId == null || cycle == null) throw new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND");
        if (cycle.getTenant() == null || !cycle.getTenant().getId().equals(tenantId)) throw new BusinessException("BILLING_FORBIDDEN");
        if (cycle.getStatus() != BillingCycleStatus.USAGE_FINALIZED) throw new BusinessException("BILLING_CYCLE_USAGE_NOT_FINALIZED");

        TenantBillingInvoice existing = invoiceRepository.findByTenantIdAndBillingCycleId(tenantId, cycle.getId()).orElse(null);
        if (existing != null) return existing;

        String number = sequenceService.nextNumber(tenantId, LocalDateTime.now());

        TenantBillingInvoice inv = new TenantBillingInvoice();
        inv.setTenant(cycle.getTenant());
        inv.setSubscription(cycle.getSubscription());
        inv.setBillingCycle(cycle);
        inv.setInvoiceNumber(number);
        inv.setStatus(TenantBillingInvoiceStatus.DRAFT);
        inv.setCurrency(cycle.getSubscription() != null ? cycle.getSubscription().getCurrency() : "AOA");
        inv = invoiceRepository.save(inv);

        List<TenantBillingInvoiceLine> lines = new ArrayList<>();
        BillingPlan plan = cycle.getSubscription() != null ? cycle.getSubscription().getBillingPlan() : null;

        // Base price
        TenantBillingInvoiceLine base = new TenantBillingInvoiceLine();
        base.setInvoice(inv);
        base.setTenant(cycle.getTenant());
        base.setMetricCode(UsageMetricCode.PAYMENT_CONFIRMED);
        base.setDescription("Base price (" + (plan != null ? plan.getName() : "plan") + ")");
        base.setQuantity(scaleQty(BigDecimal.ONE));
        base.setUnitPrice(scaleMoney(plan != null ? plan.getBasePrice() : BigDecimal.ZERO));
        base.setAmount(scaleMoney(base.getUnitPrice()));
        base.setIncludedQuantity(scaleQty(BigDecimal.ZERO));
        base.setOverageQuantity(scaleQty(BigDecimal.ZERO));
        base.setPeriodStart(cycle.getPeriodStart());
        base.setPeriodEnd(cycle.getPeriodEnd());
        lines.add(lineRepository.save(base));

        List<UsageAggregation> aggs = aggregationRepository.findByTenantIdAndBillingCycleIdOrderByMetricCodeAsc(tenantId, cycle.getId());
        for (UsageAggregation a : aggs) {
            if (a == null) continue;
            TenantBillingInvoiceLine l = new TenantBillingInvoiceLine();
            l.setInvoice(inv);
            l.setTenant(cycle.getTenant());
            l.setMetricCode(a.getMetricCode());
            l.setDescription("Usage: " + a.getMetricCode().name());
            l.setQuantity(scaleQty(nz(a.getBillableQuantity())));
            BigDecimal unit = BigDecimal.ZERO;
            if (plan != null && a.getMetricCode() == UsageMetricCode.PAYMENT_CONFIRMED) {
                unit = nz(plan.getOveragePricePerTransaction());
            }
            l.setUnitPrice(scaleMoney(unit));
            l.setIncludedQuantity(scaleQty(nz(a.getIncludedQuantity())));
            l.setOverageQuantity(scaleQty(nz(a.getOverageQuantity())));
            l.setAmount(scaleMoney(nz(a.getCalculatedChargeAmount())));
            l.setPeriodStart(a.getPeriodStart());
            l.setPeriodEnd(a.getPeriodEnd());
            lines.add(lineRepository.save(l));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (TenantBillingInvoiceLine l : lines) {
            subtotal = subtotal.add(nz(l.getAmount()));
        }

        BigDecimal total = subtotal;
        if (plan != null) {
            BigDecimal minFee = nz(plan.getMinimumMonthlyFee());
            if (minFee.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(minFee) < 0) {
                total = minFee;
            }
        }

        inv.setSubtotalAmount(scaleMoney(subtotal));
        inv.setDiscountAmount(scaleMoney(BigDecimal.ZERO));
        inv.setTaxAmount(scaleMoney(BigDecimal.ZERO));
        inv.setTotalAmount(scaleMoney(total));
        inv.setTotalPaidAmount(scaleMoney(BigDecimal.ZERO));
        inv.setOutstandingAmount(scaleMoney(total));
        inv.setCollectionStatus(com.restaurante.model.enums.TenantBillingCollectionStatus.CURRENT);
        inv = invoiceRepository.save(inv);

        String invoiceHash = hashService.hash(inv, lines);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_INVOICE_GENERATED,
                OperationalEntityType.TENANT_BILLING_INVOICE,
                inv.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Invoice interna gerada",
                Map.of(
                        "tenantId", tenantId,
                        "cycleId", cycle.getId(),
                        "invoiceId", inv.getId(),
                        "invoiceNumber", inv.getInvoiceNumber(),
                        "totalAmount", inv.getTotalAmount(),
                        "invoiceHash", invoiceHash
                ),
                null,
                null
        );

        return inv;
    }

    @Transactional
    public TenantBillingInvoice issue(Long tenantId, Long invoiceId) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        if (inv.getStatus() != TenantBillingInvoiceStatus.DRAFT) throw new BusinessException("TENANT_BILLING_INVOICE_INVALID_STATE");
        inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        inv.setIssuedAt(LocalDateTime.now());
        if (inv.getDueAt() == null) inv.setDueAt(inv.getIssuedAt().plusDays(7));
        if (inv.getOutstandingAmount() == null || inv.getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0) {
            inv.setOutstandingAmount(scaleMoney(nz(inv.getTotalAmount())));
        }
        inv = invoiceRepository.save(inv);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_INVOICE_ISSUED,
                OperationalEntityType.TENANT_BILLING_INVOICE,
                inv.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Invoice interna emitida",
                Map.of("tenantId", tenantId, "invoiceId", inv.getId(), "invoiceNumber", inv.getInvoiceNumber()),
                null,
                null
        );
        return inv;
    }

    @Transactional
    public TenantBillingInvoice markPaid(Long tenantId, Long invoiceId) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        if (inv.getStatus() != TenantBillingInvoiceStatus.ISSUED && inv.getStatus() != TenantBillingInvoiceStatus.OVERDUE) {
            throw new BusinessException("TENANT_BILLING_INVOICE_INVALID_STATE");
        }
        inv.setStatus(TenantBillingInvoiceStatus.PAID);
        inv.setPaidAt(LocalDateTime.now());
        inv.setTotalPaidAmount(scaleMoney(nz(inv.getTotalAmount())));
        inv.setOutstandingAmount(scaleMoney(BigDecimal.ZERO));
        inv.setLastPaymentAt(inv.getPaidAt());
        inv.setCollectionStatus(com.restaurante.model.enums.TenantBillingCollectionStatus.CLEARED);
        inv.setOverdueAt(null);
        inv.setGracePeriodEndsAt(null);
        inv = invoiceRepository.save(inv);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_INVOICE_MARKED_PAID,
                OperationalEntityType.TENANT_BILLING_INVOICE,
                inv.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Invoice marcada como paga",
                Map.of("tenantId", tenantId, "invoiceId", inv.getId(), "invoiceNumber", inv.getInvoiceNumber()),
                null,
                null
        );
        return inv;
    }

    @Transactional
    public TenantBillingInvoice cancel(Long tenantId, Long invoiceId, String reason) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        if (inv.getStatus() == TenantBillingInvoiceStatus.PAID) throw new BusinessException("TENANT_BILLING_INVOICE_ALREADY_PAID");
        if (inv.getStatus() == TenantBillingInvoiceStatus.CANCELLED) return inv;
        inv.setStatus(TenantBillingInvoiceStatus.CANCELLED);
        inv.setCancelledAt(LocalDateTime.now());
        inv.setNotes(reason);
        inv = invoiceRepository.save(inv);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_INVOICE_CANCELLED,
                OperationalEntityType.TENANT_BILLING_INVOICE,
                inv.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Invoice cancelada",
                Map.of("tenantId", tenantId, "invoiceId", inv.getId(), "invoiceNumber", inv.getInvoiceNumber()),
                null,
                null
        );
        return inv;
    }
}
