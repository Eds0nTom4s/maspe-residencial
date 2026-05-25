package com.restaurante.billing;

import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.billing.enabled=true"
})
public class BillingCycleServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleService cycleService;
    @Autowired private BillingCycleRepository cycleRepository;

    @Test
    @Transactional
    void criaECapturaCicloCorrenteSemSobreposicao() {
        Tenant tenant = criarTenant();
        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-CYCLE-" + System.nanoTime());
        plan.setName("Plan Cycle");
        plan.setCurrency("AOA");
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now);
        sub.setCurrentPeriodStart(now.minusDays(10));
        sub.setCurrentPeriodEnd(now.plusDays(20));
        sub.setBillingAnchorDay(1);
        sub.setCurrency("AOA");
        sub.setAutoRenew(true);
        sub = subscriptionRepository.saveAndFlush(sub);

        var c1 = cycleService.getOrOpenCurrentCycle(sub, now);
        var c2 = cycleService.getOrOpenCurrentCycle(sub, now.plusMinutes(1));

        assertThat(c1.getId()).isNotNull();
        assertThat(c2.getId()).isEqualTo(c1.getId());
        assertThat(cycleRepository.findByTenantIdAndId(tenant.getId(), c1.getId())).isPresent();
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Cycle " + suffix);
        t.setSlug("tenant-cycle-" + suffix);
        t.setTenantCode("TC" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}
