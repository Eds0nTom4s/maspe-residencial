package com.restaurante.billing.service;

import com.restaurante.billing.hash.UsageAggregationHashService;
import com.restaurante.billing.repository.UsageAdjustmentRepository;
import com.restaurante.billing.repository.UsageAggregationRepository;
import com.restaurante.billing.repository.UsageEventRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.UsageAggregationStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.UsageEventStatus;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;
import static com.restaurante.billing.util.BillingMath.scaleQty;

@Service
@RequiredArgsConstructor
public class UsageAggregationService {

    private final UsageEventRepository usageEventRepository;
    private final UsageAdjustmentRepository adjustmentRepository;
    private final UsageAggregationRepository aggregationRepository;
    private final UsageAggregationHashService hashService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public UsageAggregation aggregateForPeriod(Long tenantId,
                                               TenantSubscription subscription,
                                               UsageMetricCode metric,
                                               LocalDateTime periodStart,
                                               LocalDateTime periodEnd) {
        if (tenantId == null || subscription == null) throw new BusinessException("USAGE_AGGREGATION_INVALID_PERIOD");
        if (metric == null || periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            throw new BusinessException("USAGE_AGGREGATION_INVALID_PERIOD");
        }

        List<UsageEvent> events = usageEventRepository.findRecordedInPeriodByMetric(
                tenantId,
                UsageEventStatus.RECORDED,
                metric,
                periodStart,
                periodEnd
        );

        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (UsageEvent e : events) {
            qty = qty.add(nz(e.getQuantity()));
            amount = amount.add(nz(e.getAmount()));
        }

        // Ajustes (MVP manual)
        var adjustments = adjustmentRepository.findByTenantIdAndMetricCodeOrderByCreatedAtDesc(tenantId, metric);
        for (var a : adjustments) {
            qty = qty.add(nz(a.getQuantityDelta()));
            amount = amount.add(nz(a.getAmountDelta()));
        }

        qty = scaleQty(qty);
        amount = scaleMoney(amount);

        UsageAggregation agg = aggregationRepository.findLatestForPeriod(tenantId, subscription.getId(), metric, periodStart, periodEnd).orElse(null);
        if (agg == null) {
            agg = new UsageAggregation();
            agg.setTenant(subscription.getTenant());
            agg.setSubscription(subscription);
            agg.setMetricCode(metric);
            agg.setPeriodStart(periodStart);
            agg.setPeriodEnd(periodEnd);
        } else {
            if (agg.getStatus() == UsageAggregationStatus.FINALIZED) {
                throw new BusinessException("USAGE_AGGREGATION_INVALID_PERIOD");
            }
        }

        agg.setQuantityTotal(qty);
        agg.setAmountTotal(amount);

        BillingPlan plan = subscription.getBillingPlan();
        boolean trialing = subscription.getStatus() == TenantSubscriptionStatus.TRIALING;

        BigDecimal billableQty = trialing ? BigDecimal.ZERO : qty;
        BigDecimal includedQty = BigDecimal.ZERO;
        BigDecimal overageQty = BigDecimal.ZERO;
        BigDecimal charge = BigDecimal.ZERO;

        if (metric == UsageMetricCode.PAYMENT_CONFIRMED && plan != null) {
            long included = plan.getIncludedTransactions() != null ? plan.getIncludedTransactions() : 0L;
            BigDecimal includedBd = scaleQty(BigDecimal.valueOf(Math.max(0L, included)));
            includedQty = qty.min(includedBd);
            overageQty = qty.subtract(includedQty);
            if (overageQty.compareTo(BigDecimal.ZERO) < 0) overageQty = BigDecimal.ZERO;

            if (!trialing) {
                BigDecimal unit = nz(plan.getOveragePricePerTransaction());
                charge = scaleMoney(overageQty.multiply(unit));
                BigDecimal pct = nz(plan.getTransactionFeePercentage());
                if (pct.compareTo(BigDecimal.ZERO) > 0) {
                    charge = scaleMoney(charge.add(amount.multiply(pct)));
                }
            }
        }

        agg.setBillableQuantity(scaleQty(billableQty));
        agg.setIncludedQuantity(scaleQty(includedQty));
        agg.setOverageQuantity(scaleQty(overageQty));
        agg.setCalculatedChargeAmount(scaleMoney(charge));
        agg.setCurrency(subscription.getCurrency() != null ? subscription.getCurrency() : "AOA");

        agg = aggregationRepository.save(agg);
        String hash = hashService.hash(agg);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.USAGE_AGGREGATION_CREATED,
                OperationalEntityType.USAGE_AGGREGATION,
                agg.getId(),
                OperationalOrigem.SYSTEM,
                "Usage aggregation criada/atualizada",
                Map.of(
                        "tenantId", tenantId,
                        "subscriptionId", subscription.getId(),
                        "metricCode", metric.name(),
                        "periodStart", periodStart,
                        "periodEnd", periodEnd,
                        "quantityTotal", agg.getQuantityTotal(),
                        "chargeAmount", agg.getCalculatedChargeAmount(),
                        "hash", hash
                ),
                null,
                null
        );

        return agg;
    }
}
