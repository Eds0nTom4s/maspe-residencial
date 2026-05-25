package com.restaurante.billing;

import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.UsageAggregationService;
import com.restaurante.billing.service.UsageMeteringService;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.billing.enabled=true"
})
public class UsageAggregationServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private UsageMeteringService meteringService;
    @Autowired private UsageAggregationService aggregationService;

    @Test
    @Transactional
    void calculaIncludedEOverageParaPaymentConfirmed() {
        Tenant tenant = criarTenant();
        BillingPlan plan = criarPlan("PLAN-A", 1L, new BigDecimal("2.00"));
        TenantSubscription sub = criarSubscription(tenant, plan, TenantSubscriptionStatus.ACTIVE);

        LocalDateTime start = sub.getCurrentPeriodStart();
        LocalDateTime end = sub.getCurrentPeriodEnd();

        meteringService.recordUsageEvent(tenant, null, UsageMetricCode.PAYMENT_CONFIRMED, "TEST", "PAYMENT", 1L,
                "tenant:" + tenant.getId() + ":p1", start.plusMinutes(1), BigDecimal.ONE, new BigDecimal("10.00"), "AOA", null);
        meteringService.recordUsageEvent(tenant, null, UsageMetricCode.PAYMENT_CONFIRMED, "TEST", "PAYMENT", 2L,
                "tenant:" + tenant.getId() + ":p2", start.plusMinutes(2), BigDecimal.ONE, new BigDecimal("20.00"), "AOA", null);

        var agg = aggregationService.aggregateForPeriod(tenant.getId(), sub, UsageMetricCode.PAYMENT_CONFIRMED, start, end);

        assertThat(agg.getQuantityTotal()).isEqualByComparingTo(new BigDecimal("2.000000"));
        assertThat(agg.getIncludedQuantity()).isEqualByComparingTo(new BigDecimal("1.000000"));
        assertThat(agg.getOverageQuantity()).isEqualByComparingTo(new BigDecimal("1.000000"));
        assertThat(agg.getCalculatedChargeAmount()).isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    @Test
    @Transactional
    void trialingAgregaSemCobrar() {
        Tenant tenant = criarTenant();
        BillingPlan plan = criarPlan("PLAN-T", 0L, new BigDecimal("5.00"));
        TenantSubscription sub = criarSubscription(tenant, plan, TenantSubscriptionStatus.TRIALING);

        LocalDateTime start = sub.getCurrentPeriodStart();
        LocalDateTime end = sub.getCurrentPeriodEnd();

        meteringService.recordUsageEvent(tenant, null, UsageMetricCode.PAYMENT_CONFIRMED, "TEST", "PAYMENT", 1L,
                "tenant:" + tenant.getId() + ":p3", start.plusMinutes(1), BigDecimal.ONE, new BigDecimal("10.00"), "AOA", null);

        var agg = aggregationService.aggregateForPeriod(tenant.getId(), sub, UsageMetricCode.PAYMENT_CONFIRMED, start, end);
        assertThat(agg.getCalculatedChargeAmount()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(agg.getBillableQuantity()).isEqualByComparingTo(new BigDecimal("0.000000"));
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Agg " + suffix);
        t.setSlug("tenant-agg-" + suffix);
        t.setTenantCode("TA" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private BillingPlan criarPlan(String code, Long includedTx, BigDecimal overage) {
        BillingPlan p = new BillingPlan();
        p.setCode(code + "-" + (System.nanoTime() % 1_000_000));
        p.setName("Plan " + code);
        p.setStatus(BillingPlanStatus.ACTIVE);
        p.setBillingInterval(BillingInterval.MONTHLY);
        p.setCurrency("AOA");
        p.setBasePrice(new BigDecimal("0.00"));
        p.setIncludedTransactions(includedTx);
        p.setOveragePricePerTransaction(overage);
        p.setTransactionFeePercentage(BigDecimal.ZERO);
        p.setMinimumMonthlyFee(BigDecimal.ZERO);
        return planRepository.saveAndFlush(p);
    }

    private TenantSubscription criarSubscription(Tenant tenant, BillingPlan plan, TenantSubscriptionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        TenantSubscription s = new TenantSubscription();
        s.setTenant(tenant);
        s.setBillingPlan(plan);
        s.setStatus(status);
        s.setStartedAt(now);
        s.setCurrentPeriodStart(now.minusDays(1));
        s.setCurrentPeriodEnd(now.plusDays(30));
        s.setBillingAnchorDay(1);
        s.setCurrency("AOA");
        s.setAutoRenew(true);
        return subscriptionRepository.saveAndFlush(s);
    }
}

