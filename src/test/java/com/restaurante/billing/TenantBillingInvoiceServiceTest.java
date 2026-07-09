package com.restaurante.billing;

import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.repository.UsageAggregationRepository;
import com.restaurante.billing.service.TenantBillingInvoiceService;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.enums.BillingCycleStatus;
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
        "consuma.billing.enabled=true",
        "consuma.billing.invoice.sequence-prefix=CONS-BILL"
})
public class TenantBillingInvoiceServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleRepository cycleRepository;
    @Autowired private UsageAggregationRepository aggregationRepository;
    @Autowired private TenantBillingInvoiceService invoiceService;

    @Test
    @Transactional
    void geraInvoiceInternaNaoDuplicaEAtribuiNumero() {
        Tenant tenant = criarTenant();

        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-INV-" + System.nanoTime());
        plan.setName("Plan Invoice");
        plan.setCurrency("AOA");
        plan.setBasePrice(new BigDecimal("10.00"));
        plan.setIncludedTransactions(1L);
        plan.setOveragePricePerTransaction(new BigDecimal("2.00"));
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now);
        sub.setCurrentPeriodStart(now.minusDays(1));
        sub.setCurrentPeriodEnd(now.plusDays(30));
        sub.setBillingAnchorDay(1);
        sub.setCurrency("AOA");
        sub.setAutoRenew(true);
        sub = subscriptionRepository.saveAndFlush(sub);

        BillingCycle cycle = new BillingCycle();
        cycle.setTenant(tenant);
        cycle.setSubscription(sub);
        cycle.setPeriodStart(sub.getCurrentPeriodStart());
        cycle.setPeriodEnd(sub.getCurrentPeriodEnd());
        cycle.setStatus(BillingCycleStatus.USAGE_FINALIZED);
        cycle.setUsageFinalizedAt(now);
        cycle = cycleRepository.saveAndFlush(cycle);

        UsageAggregation agg = new UsageAggregation();
        agg.setTenant(tenant);
        agg.setSubscription(sub);
        agg.setBillingCycle(cycle);
        agg.setMetricCode(UsageMetricCode.PAYMENT_CONFIRMED);
        agg.setPeriodStart(cycle.getPeriodStart());
        agg.setPeriodEnd(cycle.getPeriodEnd());
        agg.setQuantityTotal(new BigDecimal("2.000000"));
        agg.setBillableQuantity(new BigDecimal("2.000000"));
        agg.setIncludedQuantity(new BigDecimal("1.000000"));
        agg.setOverageQuantity(new BigDecimal("1.000000"));
        agg.setCalculatedChargeAmount(new BigDecimal("2.0000"));
        agg.setCurrency("AOA");
        aggregationRepository.saveAndFlush(agg);

        var inv1 = invoiceService.generateForCycle(tenant.getId(), cycle);
        var inv2 = invoiceService.generateForCycle(tenant.getId(), cycle);

        assertThat(inv1.getId()).isNotNull();
        assertThat(inv2.getId()).isEqualTo(inv1.getId());
        assertThat(inv1.getInvoiceNumber()).startsWith("CONS-BILL-");
        assertThat(inv1.getTotalAmount()).isEqualByComparingTo(new BigDecimal("12.0000"));
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Invoice " + suffix);
        t.setSlug("tenant-inv-" + suffix);
        t.setTenantCode("TI" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

