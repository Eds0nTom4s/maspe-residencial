package com.restaurante.billing.evidence;

import com.restaurante.billing.config.BillingProperties;
import com.restaurante.billing.hash.TenantBillingInvoiceHashService;
import com.restaurante.billing.hash.UsageAggregationHashService;
import com.restaurante.billing.repository.TenantBillingInvoiceLineRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.repository.UsageEventRepository;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.billing.service.UsageAggregationService;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceInvoiceItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceSectionDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.BillingEvidenceUsageItemDTO;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingInvoiceLine;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.enums.BillingCycleStatus;
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

