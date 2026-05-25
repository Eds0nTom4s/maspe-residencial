package com.restaurante.billing;

import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantBillingPaymentRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.service.TenantBillingPaymentService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingPayment;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingPaymentMethod;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.billing.enabled=true"
})
public class TenantBillingPaymentServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private BillingCycleRepository cycleRepository;
    @Autowired private TenantBillingInvoiceRepository invoiceRepository;
    @Autowired private TenantBillingPaymentRepository paymentRepository;
    @Autowired private TenantBillingPaymentService paymentService;

    @Test
    @Transactional
    void pagamentoParcialEDepoisTotalAtualizaInvoice() {
        TenantBillingInvoice inv = criarInvoiceIssuida(new BigDecimal("100.00"));

        TenantBillingPayment p1 = paymentService.recordPayment(
                inv.getTenant().getId(),
                inv.getId(),
                new BigDecimal("40.00"),
                "AOA",
                TenantBillingPaymentMethod.BANK_TRANSFER,
                LocalDateTime.now().minusHours(1),
                "TRF-001",
                "proof-001",
                "parcial",
                null,
                false
        );
        assertThat(p1.getStatus()).isEqualTo(TenantBillingPaymentStatus.RECORDED);

        paymentService.confirmPayment(inv.getTenant().getId(), p1.getId());
        TenantBillingInvoice invAfter1 = invoiceRepository.findByTenantIdAndId(inv.getTenant().getId(), inv.getId()).orElseThrow();
        assertThat(invAfter1.getStatus()).isEqualTo(TenantBillingInvoiceStatus.PARTIALLY_PAID);
        assertThat(invAfter1.getTotalPaidAmount()).isEqualByComparingTo("40.0000");
        assertThat(invAfter1.getOutstandingAmount()).isEqualByComparingTo("60.0000");

        TenantBillingPayment p2 = paymentService.recordPayment(
                inv.getTenant().getId(),
                inv.getId(),
                new BigDecimal("60.00"),
                "AOA",
                TenantBillingPaymentMethod.MANUAL_BANK_CONFIRMATION,
                LocalDateTime.now(),
                "TRF-002",
                "proof-002",
                "saldo",
                null,
                true
        );
        assertThat(p2.getStatus()).isEqualTo(TenantBillingPaymentStatus.CONFIRMED);

        TenantBillingInvoice invAfter2 = invoiceRepository.findByTenantIdAndId(inv.getTenant().getId(), inv.getId()).orElseThrow();
        assertThat(invAfter2.getStatus()).isEqualTo(TenantBillingInvoiceStatus.PAID);
        assertThat(invAfter2.getOutstandingAmount()).isEqualByComparingTo("0.0000");
        assertThat(invAfter2.getCollectionStatus()).isEqualTo(TenantBillingCollectionStatus.CLEARED);
    }

    @Test
    @Transactional
    void overpaymentBloqueado() {
        TenantBillingInvoice inv = criarInvoiceIssuida(new BigDecimal("50.00"));
        assertThatThrownBy(() -> paymentService.recordPayment(
                inv.getTenant().getId(),
                inv.getId(),
                new BigDecimal("60.00"),
                "AOA",
                TenantBillingPaymentMethod.BANK_TRANSFER,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                false
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("TENANT_BILLING_PAYMENT_OVERPAYMENT_NOT_ALLOWED");
    }

    @Test
    @Transactional
    void currencyMismatchFalha() {
        TenantBillingInvoice inv = criarInvoiceIssuida(new BigDecimal("50.00"));
        assertThatThrownBy(() -> paymentService.recordPayment(
                inv.getTenant().getId(),
                inv.getId(),
                new BigDecimal("10.00"),
                "USD",
                TenantBillingPaymentMethod.BANK_TRANSFER,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                false
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("TENANT_BILLING_PAYMENT_CURRENCY_MISMATCH");
    }

    private TenantBillingInvoice criarInvoiceIssuida(BigDecimal total) {
        Tenant tenant = criarTenant();

        BillingPlan plan = new BillingPlan();
        plan.setCode("PLAN-PAY-" + System.nanoTime());
        plan.setName("Plan Pay");
        plan.setCurrency("AOA");
        plan = planRepository.saveAndFlush(plan);

        LocalDateTime now = LocalDateTime.now();
        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setStartedAt(now.minusDays(5));
        sub.setCurrentPeriodStart(now.minusDays(10));
        sub.setCurrentPeriodEnd(now.plusDays(20));
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
        inv.setInvoiceNumber("CONS-BILL-TEST-" + System.nanoTime());
        inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        inv.setCurrency("AOA");
        inv.setSubtotalAmount(total);
        inv.setDiscountAmount(BigDecimal.ZERO);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(total);
        inv.setTotalPaidAmount(BigDecimal.ZERO);
        inv.setOutstandingAmount(total);
        inv.setIssuedAt(now.minusDays(1));
        inv.setDueAt(now.plusDays(5));
        inv.setCollectionStatus(TenantBillingCollectionStatus.CURRENT);
        return invoiceRepository.saveAndFlush(inv);
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Pay " + suffix);
        t.setSlug("tenant-pay-" + suffix);
        t.setTenantCode("TP" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

