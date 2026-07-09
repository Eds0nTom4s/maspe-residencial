package com.restaurante.billing;

import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.TenantBillingCollectionService;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.TenantTipo;
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
public class TenantBillingCollectionServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleRepository cycleRepository;
    @Autowired private TenantBillingInvoiceRepository invoiceRepository;
    @Autowired private TenantBillingCollectionService collectionService;

    @Test
    @Transactional
    void invoiceVencidaViraOverdueECalculaGrace() {
        TenantBillingInvoice inv = criarInvoiceVencida(new BigDecimal("100.00"));

        TenantBillingCollectionStatus status = collectionService.evaluateTenantBillingStatus(inv.getTenant().getId(), LocalDateTime.now());
        assertThat(status).isIn(TenantBillingCollectionStatus.OVERDUE, TenantBillingCollectionStatus.IN_GRACE_PERIOD);

        TenantBillingInvoice refreshed = invoiceRepository.findByTenantIdAndId(inv.getTenant().getId(), inv.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(TenantBillingInvoiceStatus.OVERDUE);
        assertThat(refreshed.getOverdueAt()).isNotNull();
        assertThat(refreshed.getGracePeriodEndsAt()).isNotNull();
    }

    private TenantBillingInvoice criarInvoiceVencida(BigDecimal total) {
        Tenant tenant = criarTenant();

        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-COLL-" + System.nanoTime());
        plan.setName("Plan Coll");
        plan.setCurrency("AOA");
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now.minusDays(30));
        sub.setCurrentPeriodStart(now.minusDays(30));
        sub.setCurrentPeriodEnd(now.plusDays(1));
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
        cycle = cycleRepository.saveAndFlush(cycle);

        TenantBillingInvoice inv = new TenantBillingInvoice();
        inv.setTenant(tenant);
        inv.setSubscription(sub);
        inv.setBillingCycle(cycle);
        inv.setInvoiceNumber("CONS-BILL-OD-" + System.nanoTime());
        inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        inv.setCurrency("AOA");
        inv.setSubtotalAmount(total);
        inv.setDiscountAmount(BigDecimal.ZERO);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(total);
        inv.setTotalPaidAmount(BigDecimal.ZERO);
        inv.setOutstandingAmount(total);
        inv.setIssuedAt(now.minusDays(20));
        inv.setDueAt(now.minusDays(10));
        inv.setCollectionStatus(TenantBillingCollectionStatus.CURRENT);
        return invoiceRepository.saveAndFlush(inv);
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Coll " + suffix);
        t.setSlug("tenant-coll-" + suffix);
        t.setTenantCode("TCOL" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

