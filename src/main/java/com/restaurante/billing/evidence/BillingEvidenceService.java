package com.restaurante.billing.evidence;

import com.restaurante.billing.config.BillingProperties;
import com.restaurante.billing.hash.TenantBillingInvoiceHashService;
import com.restaurante.billing.hash.TenantBillingPaymentHashService;
import com.restaurante.billing.hash.UsageAggregationHashService;
import com.restaurante.billing.repository.TenantBillingInvoiceLineRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantBillingPaymentRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.repository.UsageEventRepository;
import com.restaurante.billing.service.TenantBillingCollectionService;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.billing.service.UsageAggregationService;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceInvoiceItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidencePaymentItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceSectionDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceUsageItemDTO;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingInvoiceLine;
import com.restaurante.model.entity.TenantBillingPayment;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
import com.restaurante.model.enums.UsageEventStatus;
import com.restaurante.model.enums.UsageMetricCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;

@Service
@RequiredArgsConstructor
public class BillingEvidenceService {

    private final BillingProperties props;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final BillingCycleService cycleService;
    private final UsageEventRepository usageEventRepository;
    private final UsageAggregationService aggregationService;
    private final UsageAggregationHashService aggregationHashService;
    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingInvoiceLineRepository invoiceLineRepository;
    private final TenantBillingInvoiceHashService invoiceHashService;
    private final TenantBillingPaymentRepository paymentRepository;
    private final TenantBillingPaymentHashService paymentHashService;
    private final TenantBillingCollectionService collectionService;

    @Transactional
    public BillingEvidenceSectionDTO buildForTurno(Long tenantId, LocalDateTime generatedAt) {
        if (!props.isEnabled() || !props.getEvidence().isEnabled()) return null;
        if (tenantId == null) return null;

        BillingEvidenceSectionDTO out = new BillingEvidenceSectionDTO();
        out.setGeneratedAt(generatedAt != null ? generatedAt : LocalDateTime.now());
        out.setTenantId(tenantId);

        List<String> warnings = new ArrayList<>();

        TenantSubscription sub = subscriptionRepository.findTopByTenantIdOrderByIdDesc(tenantId).orElse(null);
        if (sub == null) {
            warnings.add("TENANT_WITHOUT_SUBSCRIPTION");
            out.setWarnings(warnings);
            return out;
        }
        out.setSubscriptionId(sub.getId());

        BillingCycle cycle = cycleService.getOrOpenCurrentCycle(sub, out.getGeneratedAt());
        if (cycle == null) {
            warnings.add("ACTIVE_SUBSCRIPTION_WITHOUT_BILLING_CYCLE");
            out.setWarnings(warnings);
            return out;
        }
        out.setBillingCycleId(cycle.getId());
        out.setPeriodStart(cycle.getPeriodStart());
        out.setPeriodEnd(cycle.getPeriodEnd());

        LocalDateTime periodEnd = out.getGeneratedAt().isBefore(cycle.getPeriodEnd()) ? out.getGeneratedAt() : cycle.getPeriodEnd();
        int totalUsageEvents = usageEventRepository.findRecordedInPeriod(tenantId, UsageEventStatus.RECORDED, cycle.getPeriodStart(), periodEnd).size();
        out.setTotalUsageEvents(totalUsageEvents);

        UsageAggregation paymentAgg = aggregationService.aggregateForPeriod(tenantId, sub, UsageMetricCode.PAYMENT_CONFIRMED, cycle.getPeriodStart(), cycle.getPeriodEnd());
        paymentAgg.setBillingCycle(cycle);

        BigDecimal billable = nz(paymentAgg.getBillableQuantity());
        out.setBillableTransactions(billable);
        out.setIncludedTransactions(nz(paymentAgg.getIncludedQuantity()));
        out.setOverageTransactions(nz(paymentAgg.getOverageQuantity()));

        BillingPlan plan = sub.getBillingPlan();
        out.setBasePrice(scaleMoney(plan != null ? plan.getBasePrice() : BigDecimal.ZERO));
        out.setUsageChargeAmount(scaleMoney(paymentAgg.getCalculatedChargeAmount()));

        BigDecimal total = nz(out.getBasePrice()).add(nz(out.getUsageChargeAmount()));
        if (plan != null && nz(plan.getMinimumMonthlyFee()).compareTo(BigDecimal.ZERO) > 0 && total.compareTo(nz(plan.getMinimumMonthlyFee())) < 0) {
            total = nz(plan.getMinimumMonthlyFee());
        }
        out.setTotalBillingAmount(scaleMoney(total));

        if (cycle.getStatus() == BillingCycleStatus.OPEN) {
            warnings.add("BILLING_CYCLE_OPEN");
        } else if (cycle.getStatus() == BillingCycleStatus.USAGE_FINALIZED) {
            warnings.add("BILLING_CYCLE_NOT_INVOICED");
        }

        List<BillingEvidenceUsageItemDTO> usageItems = new ArrayList<>();
        BillingEvidenceUsageItemDTO it = new BillingEvidenceUsageItemDTO();
        it.setMetricCode(UsageMetricCode.PAYMENT_CONFIRMED.name());
        it.setQuantityTotal(paymentAgg.getQuantityTotal());
        it.setIncludedQuantity(paymentAgg.getIncludedQuantity());
        it.setOverageQuantity(paymentAgg.getOverageQuantity());
        it.setCalculatedChargeAmount(paymentAgg.getCalculatedChargeAmount());
        it.setAggregationHash(aggregationHashService.hash(paymentAgg));
        usageItems.add(it);
        out.setUsageAggregations(usageItems);

        TenantBillingInvoice invoice = invoiceRepository.findByTenantIdAndBillingCycleId(tenantId, cycle.getId()).orElse(null);
        if (invoice == null) {
            warnings.add("BILLING_CYCLE_NOT_INVOICED");
        } else {
            out.setInvoiceId(invoice.getId());
            out.setInvoiceStatus(invoice.getStatus() != null ? invoice.getStatus().name() : null);
        }

        // Prompt 48: invoice/payment rollups (tenant-wide)
        List<TenantBillingInvoice> recentInvoices = invoiceRepository.findByTenantIdOrderByIdDesc(tenantId);
        int totalInvoices = 0, issuedInvoices = 0, paidInvoices = 0, partialInvoices = 0, overdueInvoices = 0, cancelledInvoices = 0;
        BigDecimal totalInvoicedAmount = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;
        BigDecimal totalOutstandingAmount = BigDecimal.ZERO;
        BigDecimal totalOverdueAmount = BigDecimal.ZERO;
        LocalDateTime graceEnds = null;
        for (TenantBillingInvoice inv : recentInvoices.stream().limit(200).toList()) {
            if (inv == null) continue;
            totalInvoices++;
            totalInvoicedAmount = totalInvoicedAmount.add(nz(inv.getTotalAmount()));
            totalPaidAmount = totalPaidAmount.add(nz(inv.getTotalPaidAmount()));
            totalOutstandingAmount = totalOutstandingAmount.add(nz(inv.getOutstandingAmount()));
            if (inv.getStatus() == TenantBillingInvoiceStatus.ISSUED) issuedInvoices++;
            if (inv.getStatus() == TenantBillingInvoiceStatus.PAID) paidInvoices++;
            if (inv.getStatus() == TenantBillingInvoiceStatus.PARTIALLY_PAID) partialInvoices++;
            if (inv.getStatus() == TenantBillingInvoiceStatus.OVERDUE) {
                overdueInvoices++;
                totalOverdueAmount = totalOverdueAmount.add(nz(inv.getOutstandingAmount()));
                if (inv.getGracePeriodEndsAt() != null && (graceEnds == null || inv.getGracePeriodEndsAt().isBefore(graceEnds))) {
                    graceEnds = inv.getGracePeriodEndsAt();
                }
            }
            if (inv.getStatus() == TenantBillingInvoiceStatus.CANCELLED || inv.getStatus() == TenantBillingInvoiceStatus.VOIDED) cancelledInvoices++;
        }
        out.setTotalInvoices(totalInvoices);
        out.setIssuedInvoices(issuedInvoices);
        out.setPaidInvoices(paidInvoices);
        out.setPartiallyPaidInvoices(partialInvoices);
        out.setOverdueInvoices(overdueInvoices);
        out.setCancelledInvoices(cancelledInvoices);
        out.setTotalInvoicedAmount(scaleMoney(totalInvoicedAmount));
        out.setTotalPaidAmount(scaleMoney(totalPaidAmount));
        out.setTotalOutstandingAmount(scaleMoney(totalOutstandingAmount));
        out.setTotalOverdueAmount(scaleMoney(totalOverdueAmount));

        TenantBillingCollectionStatus collStatus = collectionService.evaluateTenantBillingStatus(tenantId, out.getGeneratedAt());
        out.setCollectionStatus(collStatus != null ? collStatus.name() : null);
        out.setGracePeriodEndsAt(graceEnds);

        if (overdueInvoices > 0) warnings.add("BILLING_INVOICE_OVERDUE");
        if (invoice != null && invoice.getStatus() == TenantBillingInvoiceStatus.PARTIALLY_PAID) warnings.add("BILLING_PARTIAL_PAYMENT");

        // Payments for current cycle invoice (if any)
        List<BillingEvidencePaymentItemDTO> payItems = new ArrayList<>();
        if (invoice != null) {
            List<TenantBillingPayment> pays = paymentRepository.findByTenantIdAndInvoice_IdOrderByIdAsc(tenantId, invoice.getId());
            for (TenantBillingPayment p : pays.stream().limit(50).toList()) {
                BillingEvidencePaymentItemDTO pi = new BillingEvidencePaymentItemDTO();
                pi.setPaymentId(p.getId());
                pi.setInvoiceId(invoice.getId());
                pi.setStatus(p.getStatus() != null ? p.getStatus().name() : null);
                pi.setPaymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null);
                pi.setAmount(p.getAmount());
                pi.setCurrency(p.getCurrency());
                pi.setPaidAt(p.getPaidAt());
                pi.setConfirmedAt(p.getConfirmedAt());
                pi.setPaymentHash(paymentHashService.hash(p));
                payItems.add(pi);
                if (p.getStatus() == TenantBillingPaymentStatus.RECORDED) warnings.add("BILLING_PAYMENT_PENDING_CONFIRMATION");
                if (p.getStatus() == TenantBillingPaymentStatus.REJECTED) warnings.add("BILLING_PAYMENT_REJECTED");
            }
        }
        out.setBillingPayments(payItems.isEmpty() ? null : payItems);

        List<BillingEvidenceInvoiceItemDTO> invoiceItems = new ArrayList<>();
        if (invoice != null) {
            List<TenantBillingInvoiceLine> lines = invoiceLineRepository.findByTenantIdAndInvoice_IdOrderByIdAsc(tenantId, invoice.getId());
            BillingEvidenceInvoiceItemDTO invIt = new BillingEvidenceInvoiceItemDTO();
            invIt.setInvoiceId(invoice.getId());
            invIt.setInvoiceNumber(invoice.getInvoiceNumber());
            invIt.setStatus(invoice.getStatus() != null ? invoice.getStatus().name() : null);
            invIt.setSubtotalAmount(invoice.getSubtotalAmount());
            invIt.setTotalAmount(invoice.getTotalAmount());
            invIt.setIssuedAt(invoice.getIssuedAt());
            invIt.setInvoiceHash(invoiceHashService.hash(invoice, lines));
            invoiceItems.add(invIt);
        }
        out.setInvoiceLines(invoiceItems);

        out.setWarnings(warnings);
        return out;
    }
}
