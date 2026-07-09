package com.restaurante.billing;

import com.restaurante.billing.enums.TenantBillingOperationType;
import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantBillingCollectionPolicyRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.TenantAccessBillingGuardService;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.TenantBillingCollectionPolicyStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
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
public class TenantAccessBillingGuardServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleRepository cycleRepository;
    @Autowired private TenantBillingInvoiceRepository invoiceRepository;
    @Autowired private TenantBillingCollectionPolicyRepository policyRepository;
    @Autowired private TenantAccessBillingGuardService guardService;

    @Test
    @Transactional
    void suspensoBloqueiaAddDeviceMasNaoBloqueiaConfirmPaymentNoMvp() {
        TenantBillingInvoice inv = criarInvoiceVencidaHaDias(new BigDecimal("100.00"), 40);

        TenantBillingCollectionPolicy pol = new TenantBillingCollectionPolicy();
        pol.setTenant(inv.getTenant());
        pol.setGracePeriodDays(1);
        pol.setAutoMarkOverdue(true);
        pol.setSuspensionMode(TenantBillingSuspensionMode.SOFT_SUSPENSION);
        pol.setSuspensionAfterDays(5);
        pol.setRestrictNewDevices(true);
        pol.setRestrictNewOrders(false);
        pol.setRestrictAdminAccess(false);
        pol.setStatus(TenantBillingCollectionPolicyStatus.ACTIVE);
        policyRepository.saveAndFlush(pol);

        var d1 = guardService.evaluateAccess(inv.getTenant().getId(), TenantBillingOperationType.ADD_DEVICE);
        assertThat(d1.getCollectionStatus()).isIn(TenantBillingCollectionStatus.OVERDUE, TenantBillingCollectionStatus.SUSPENDED, TenantBillingCollectionStatus.SUSPENSION_WARNING, TenantBillingCollectionStatus.IN_GRACE_PERIOD);

        var d2 = guardService.evaluateAccess(inv.getTenant().getId(), TenantBillingOperationType.CONFIRM_PAYMENT);
        assertThat(d2.isAllowed()).isTrue();
    }

    private TenantBillingInvoice criarInvoiceVencidaHaDias(BigDecimal total, int daysOverdue) {
        Tenant tenant = criarTenant();

        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-GUARD-" + System.nanoTime());
        plan.setName("Plan Guard");
        plan.setCurrency("AOA");
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now.minusDays(60));
        sub.setCurrentPeriodStart(now.minusDays(60));
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
        inv.setInvoiceNumber("CONS-BILL-GUARD-" + System.nanoTime());
        inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        inv.setCurrency("AOA");
        inv.setSubtotalAmount(total);
        inv.setDiscountAmount(BigDecimal.ZERO);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(total);
        inv.setTotalPaidAmount(BigDecimal.ZERO);
        inv.setOutstandingAmount(total);
        inv.setIssuedAt(now.minusDays(daysOverdue + 1L));
        inv.setDueAt(now.minusDays(daysOverdue));
        inv.setCollectionStatus(TenantBillingCollectionStatus.CURRENT);
        return invoiceRepository.saveAndFlush(inv);
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Guard " + suffix);
        t.setSlug("tenant-guard-" + suffix);
        t.setTenantCode("TGU" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

