package com.restaurante.billing;

import com.restaurante.billing.evidence.BillingEvidenceService;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.billing.service.TenantBillingPaymentService;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingPaymentMethod;
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
        "consuma.billing.enabled=true",
        "consuma.billing.evidence.enabled=true"
})
public class TenantBillingPaymentFlowIT {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleService cycleService;
    @Autowired private TenantBillingInvoiceRepository invoiceRepository;
    @Autowired private TenantBillingPaymentService paymentService;
    @Autowired private BillingEvidenceService evidenceService;

    @Test
    @Transactional
    void fluxoPagamentoParcialDepoisTotalEvidencia() {
        Tenant tenant = criarTenant();
        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-PFLOW-" + System.nanoTime());
        plan.setName("Plan PFlow");
        plan.setCurrency("AOA");
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now.minusDays(1));
        sub.setCurrentPeriodStart(now.minusDays(1));
        sub.setCurrentPeriodEnd(now.plusDays(30));
        sub.setBillingAnchorDay(1);
        sub.setCurrency("AOA");
        sub.setAutoRenew(true);
        sub = subscriptionRepository.saveAndFlush(sub);

        BillingCycle cycle = cycleService.getOrOpenCurrentCycle(sub, now);

        TenantBillingInvoice inv = new TenantBillingInvoice();
        inv.setTenant(tenant);
        inv.setSubscription(sub);
        inv.setBillingCycle(cycle);
        inv.setInvoiceNumber("CONS-BILL-PFLOW-" + System.nanoTime());
        inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        inv.setCurrency("AOA");
        inv.setSubtotalAmount(new BigDecimal("100.00"));
        inv.setDiscountAmount(BigDecimal.ZERO);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(new BigDecimal("100.00"));
        inv.setTotalPaidAmount(BigDecimal.ZERO);
        inv.setOutstandingAmount(new BigDecimal("100.00"));
        inv.setIssuedAt(now.minusDays(1));
        inv.setDueAt(now.plusDays(7));
        inv.setCollectionStatus(TenantBillingCollectionStatus.CURRENT);
        inv = invoiceRepository.saveAndFlush(inv);

        paymentService.recordPayment(
                tenant.getId(),
                inv.getId(),
                new BigDecimal("30.00"),
                "AOA",
                TenantBillingPaymentMethod.BANK_TRANSFER,
                now.minusHours(2),
                "TRF-001",
                "proof-001",
                null,
                null,
                true
        );

        paymentService.recordPayment(
                tenant.getId(),
                inv.getId(),
                new BigDecimal("70.00"),
                "AOA",
                TenantBillingPaymentMethod.BANK_TRANSFER,
                now.minusHours(1),
                "TRF-002",
                "proof-002",
                null,
                null,
                true
        );

        TenantBillingInvoice paid = invoiceRepository.findByTenantIdAndId(tenant.getId(), inv.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(TenantBillingInvoiceStatus.PAID);

        var ev = evidenceService.buildForTurno(tenant.getId(), now);
        assertThat(ev.getBillingPayments()).isNotEmpty();
        assertThat(ev.getTotalPaidAmount()).isNotNull();
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant PFlow " + suffix);
        t.setSlug("tenant-pflow-" + suffix);
        t.setTenantCode("TPF" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

