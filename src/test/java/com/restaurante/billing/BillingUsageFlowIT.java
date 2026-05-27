package com.restaurante.billing;

import com.restaurante.billing.evidence.BillingEvidenceService;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.billing.repository.UsageAggregationRepository;
import com.restaurante.billing.service.BillingCycleService;
import com.restaurante.billing.service.TenantBillingInvoiceService;
import com.restaurante.billing.service.UsageAggregationService;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.repository.TenantRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-postgres")
@TestPropertySource(properties = {
        "consuma.billing.enabled=true",
        "consuma.billing.evidence.enabled=true"
})
public class BillingUsageFlowIT extends PostgresTestcontainersConfig {

    @Autowired private TransactionTemplate tx;
    @Autowired private ApplicationEventPublisher publisher;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BillingPlanRepository planRepository;
    @Autowired private TenantSubscriptionRepository subscriptionRepository;
    @Autowired private PagamentoGatewayRepository pagamentoRepository;

    @Autowired private BillingCycleService cycleService;
    @Autowired private BillingCycleRepository cycleRepository;
    @Autowired private UsageAggregationService aggregationService;
    @Autowired private UsageAggregationRepository aggregationRepository;
    @Autowired private TenantBillingInvoiceService invoiceService;
    @Autowired private BillingEvidenceService evidenceService;

    @Test
    void fluxoCompletoUsageAgregaInvoiceEvidence() {
        long[] ids = tx.execute(status -> {
            Tenant tenant = criarTenant();

            BillingPlan plan = new BillingPlan();
            plan.setCode("PLAN-FLOW-" + System.nanoTime());
            plan.setName("Plan Flow");
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
            sub = subscriptionRepository.saveAndFlush(sub);

            Pagamento pg = Pagamento.builder()
                    .tenant(tenant)
                    .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.PRE_PAGO)
                    .amount(new BigDecimal("10.00"))
                    .status(com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                    .observacoes("TEST")
                    .build();
            pg.confirmar();
            pg = pagamentoRepository.saveAndFlush(pg);

            publisher.publishEvent(new PaymentConfirmedForFiscalIssueEvent(
                    tenant.getId(), null, null, pg.getId(), null, null, FiscalAutoIssueSource.CASH_MANUAL_PAYMENT
            ));

            return new long[]{tenant.getId(), sub.getId(), pg.getId()};
        });

        Long tenantId = ids[0];
        tx.execute(status -> {
            TenantSubscription sub = subscriptionRepository.findById(ids[1]).orElseThrow();

            BillingCycle cycle = cycleService.getOrOpenCurrentCycle(sub, null);
            var agg = aggregationService.aggregateForPeriod(tenantId, sub, UsageMetricCode.PAYMENT_CONFIRMED, cycle.getPeriodStart(), cycle.getPeriodEnd());
            agg.setBillingCycle(cycle);
            aggregationRepository.saveAndFlush(agg);

            cycleService.finalizeUsage(tenantId, cycle.getId());
            BillingCycle finalized = cycleRepository.findByTenantIdAndId(tenantId, cycle.getId()).orElseThrow();

            var inv = invoiceService.generateForCycle(tenantId, finalized);
            invoiceService.issue(tenantId, inv.getId());

            var ev = evidenceService.buildForTurno(tenantId, LocalDateTime.now());
            assertThat(ev.getInvoiceId()).isNotNull();
            assertThat(ev.getBillableTransactions()).isNotNull();
            assertThat(ev.getUsageAggregations()).isNotEmpty();
            return null;
        });
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Flow " + suffix);
        t.setSlug("tenant-flow-" + suffix);
        t.setTenantCode("TF" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}
