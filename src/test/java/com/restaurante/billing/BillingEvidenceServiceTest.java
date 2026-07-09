package com.restaurante.billing;

import com.restaurante.billing.evidence.BillingEvidenceService;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.UsageMeteringService;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
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
        "consuma.billing.evidence.enabled=true"
})
public class BillingEvidenceServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private UsageMeteringService meteringService;
    @Autowired private BillingEvidenceService evidenceService;

    @Test
    @Transactional
    void warningQuandoTenantSemSubscription() {
        Tenant tenant = criarTenant();
        var ev = evidenceService.buildForTurno(tenant.getId(), LocalDateTime.now());
        assertThat(ev.getWarnings()).contains("TENANT_WITHOUT_SUBSCRIPTION");
    }

    @Test
    @Transactional
    void incluiAggregationsEValoresBasicos() {
        Tenant tenant = criarTenant();
        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-EV-" + System.nanoTime());
        plan.setName("Plan Ev");
        plan.setCurrency("AOA");
        plan.setBasePrice(new BigDecimal("10.00"));
        plan.setIncludedTransactions(0L);
        plan.setOveragePricePerTransaction(new BigDecimal("1.00"));
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
        subscriptionRepository.saveAndFlush(sub);

        meteringService.recordUsageEvent(tenant, null, UsageMetricCode.PAYMENT_CONFIRMED, "TEST", "PAYMENT", 1L,
                "tenant:" + tenant.getId() + ":ev1", now.minusHours(1), BigDecimal.ONE, new BigDecimal("10.00"), "AOA", null);

        var ev = evidenceService.buildForTurno(tenant.getId(), now);
        assertThat(ev.getSubscriptionId()).isNotNull();
        assertThat(ev.getBillingCycleId()).isNotNull();
        assertThat(ev.getUsageAggregations()).isNotEmpty();
        assertThat(ev.getBillableTransactions()).isNotNull();
        assertThat(ev.getTotalBillingAmount()).isNotNull();
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Ev " + suffix);
        t.setSlug("tenant-ev-" + suffix);
        t.setTenantCode("TE" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

