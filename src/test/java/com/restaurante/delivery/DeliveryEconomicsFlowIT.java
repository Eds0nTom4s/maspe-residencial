package com.restaurante.delivery;

import com.restaurante.delivery.dto.request.DeliveryFeeCalculationRequest;
import com.restaurante.delivery.repository.*;
import com.restaurante.delivery.service.*;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class DeliveryEconomicsFlowIT {

    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private OrderFulfillmentRepository orderFulfillmentRepository;
    @Autowired private CourierProfileRepository courierProfileRepository;
    @Autowired private DeliveryJobRepository deliveryJobRepository;
    @Autowired private DeliveryPricingPolicyRepository pricingPolicyRepository;
    @Autowired private CourierEarningRepository courierEarningRepository;
    @Autowired private CourierReliabilityProfileRepository reliabilityProfileRepository;
    @Autowired private CourierPenaltyEventRepository penaltyEventRepository;
    @Autowired private CourierSettlementBatchRepository settlementBatchRepository;
    @Autowired private CourierSettlementLineRepository settlementLineRepository;

    @Autowired private DeliveryFeeCalculationService calculationService;
    @Autowired private DeliveryFeeQuoteService quoteService;
    @Autowired private CourierEarningService courierEarningService;
    @Autowired private CourierReliabilityService courierReliabilityService;
    @Autowired private CourierSettlementService courierSettlementService;

    @Test
    void executeE2EDeliveryEconomicsFlow() {
        transactionTemplate.execute(status -> {
            // 1. Setup Tenant, Institution, UA, Session, Turn, Order, and Active Pricing Policy
            Tenant tenant = new Tenant();
            tenant.setNome("Consuma Economics Tenant");
            tenant.setSlug("consuma-economics-" + System.nanoTime());
            tenant.setTenantCode("ECO" + (int)(Math.random()*900 + 100));
            tenant.setTipo(TenantTipo.RESTAURANTE);
            tenant.setEstado(TenantEstado.ATIVO);
            tenant = tenantRepository.saveAndFlush(tenant);

            Instituicao inst = criarInstituicao(tenant);
            UnidadeAtendimento ua = criarUnidade(inst);
            TurnoOperacional turno = criarTurno(tenant, inst, ua);
            SessaoConsumo sessao = criarSessao(tenant, inst, ua);

            Pedido pedido = Pedido.builder()
                    .numero("PED-ECON-01")
                    .status(StatusPedido.EM_ANDAMENTO)
                    .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                    .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                    .total(new BigDecimal("5000.00"))
                    .sessaoConsumo(sessao)
                    .build();
            pedido.setTenant(tenant);
            pedido.setTurnoOperacional(turno);
            pedido = pedidoRepository.saveAndFlush(pedido);

            // Fetch global seed pricing policy or create an active one for this tenant
            DeliveryPricingPolicy policy = new DeliveryPricingPolicy();
            policy.setTenant(tenant);
            policy.setScope(DeliveryPricingPolicyScope.TENANT);
            policy.setStatus(DeliveryPricingPolicyStatus.ACTIVE);
            policy.setCurrency("AOA");
            policy.setBaseFeeAmount(new BigDecimal("800.00"));
            policy.setPerKmFeeAmount(new BigDecimal("300.00"));
            policy.setMinimumFeeAmount(new BigDecimal("1200.00"));
            policy.setConsumaCommissionPercentage(new BigDecimal("15.00"));
            policy.setCourierSharePercentage(new BigDecimal("85.00"));
            policy.setPeakMultiplier(BigDecimal.ONE);
            policy.setFragilePackageSurcharge(BigDecimal.ZERO);
            policy.setLargePackageSurcharge(BigDecimal.ZERO);
            policy.setNightSurcharge(BigDecimal.ZERO);
            policy.setTenantSubsidyEnabled(false);
            policy.setCustomerPaysDelivery(true);
            policy.setEffectiveFrom(LocalDateTime.now().minusHours(1));
            pricingPolicyRepository.saveAndFlush(policy);

            // Create OrderFulfillment
            OrderFulfillment fulfillment = new OrderFulfillment();
            fulfillment.setTenant(tenant);
            fulfillment.setPedido(pedido);
            fulfillment.setFulfillmentType(FulfillmentType.CONSUMA_NETWORK_DELIVERY);
            fulfillment.setStatus(OrderFulfillmentStatus.REQUESTED);
            fulfillment = orderFulfillmentRepository.saveAndFlush(fulfillment);

            // 2. Calculate and generate delivery fee quote
            DeliveryFeeQuote quote = quoteService.createQuote(
                    tenant.getId(),
                    pedido.getId(),
                    new BigDecimal("3.500"), // distance Km
                    PackageSize.MEDIUM,
                    false,
                    BigDecimal.ZERO
            );

            assertThat(quote).isNotNull();
            assertThat(quote.getStatus()).isEqualTo(DeliveryFeeQuoteStatus.QUOTED);
            // distance fee = 3.5 * 300 = 1050 + 800 (base) = 1850.
            assertThat(quote.getFinalDeliveryFeeAmount()).isEqualByComparingTo("1850.00");
            assertThat(quote.getCourierEarningAmount()).isEqualByComparingTo("1572.50"); // 1850 * 0.85 = 1572.50
            assertThat(quote.getConsumaCommissionAmount()).isEqualByComparingTo("277.50"); // 1850 * 0.15 = 277.50

            // Accept the quote
            quote = quoteService.acceptQuote(quote.getId());
            assertThat(quote.getStatus()).isEqualTo(DeliveryFeeQuoteStatus.ACCEPTED);

            // 3. Setup Courier Profile and Reliability
            CourierProfile courier = new CourierProfile();
            courier.setTenant(tenant);
            courier.setCourierCode("COURIER-" + System.nanoTime());
            courier.setFullName("Marcos Kwanza");
            courier.setStatus(CourierStatus.ACTIVE);
            courier.setVerificationStatus(CourierVerificationStatus.VERIFIED);
            courier.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
            courier = courierProfileRepository.saveAndFlush(courier);

            CourierReliabilityProfile relProfile = courierReliabilityService.getOrCreateProfile(courier);
            assertThat(relProfile.getScore()).isEqualTo(100);
            assertThat(relProfile.getLevel()).isEqualTo(CourierReliabilityLevel.EXCELLENT);

            // 4. Create Delivery Job linked to accepted quote
            DeliveryJob job = new DeliveryJob();
            job.setTenant(tenant);
            job.setPedido(pedido);
            job.setOrderFulfillment(fulfillment);
            job.setCourier(courier);
            job.setDeliveryFeeQuote(quote);
            job.setFinalDeliveryFee(quote.getFinalDeliveryFeeAmount());
            job.setDeliveryFeeCurrency("AOA");
            job.setCustomerPaysDeliveryAmount(quote.getCustomerPaysAmount());
            job.setTenantSubsidyAmount(quote.getTenantSubsidyAmount());
            job.setCourierEarningAmount(quote.getCourierEarningAmount());
            job.setConsumaCommissionAmount(quote.getConsumaCommissionAmount());
            job.setEarningStatus(CourierEarningStatus.NOT_EARNED);
            job.setSettlementStatus(CourierSettlementStatus.NOT_READY);
            job = deliveryJobRepository.saveAndFlush(job);

            // 5. Simulate Courier Earnings Creation upon successful job completion
            CourierEarning earning = courierEarningService.createEarning(job);
            assertThat(earning).isNotNull();
            assertThat(earning.getStatus()).isEqualTo(CourierEarningStatus.PENDING_DELIVERY);

            // Complete and earn!
            earning = courierEarningService.earn(job.getId());
            assertThat(earning.getStatus()).isEqualTo(CourierEarningStatus.PAYABLE);

            // Reward successful delivery reliability points
            relProfile = courierReliabilityService.rewardSuccessfulDelivery(courier.getId());
            assertThat(relProfile.getCompletedDeliveries()).isEqualTo(1);
            assertThat(relProfile.getScore()).isEqualTo(100); // capped at 100 max

            // 6. Test Courier Penalty system (Invite expiration / Missed)
            // Apply 3 consecutive missed invites to verify automated pause suspension triggers
            courierReliabilityService.applyPenalty(courier.getId(), CourierPenaltyType.INVITE_MISSED, null, null, "First missed invite");
            courierReliabilityService.applyPenalty(courier.getId(), CourierPenaltyType.INVITE_MISSED, null, null, "Second missed invite");
            
            // Third missed invite within 24h triggers automated 30 minute cooling-off pause
            relProfile = courierReliabilityService.applyPenalty(courier.getId(), CourierPenaltyType.INVITE_MISSED, null, null, "Third missed invite");
            
            assertThat(relProfile.getScore()).isEqualTo(94); // 100 - 2 - 2 - 2 = 94
            assertThat(courier.getStatus()).isEqualTo(CourierStatus.SUSPENDED);
            assertThat(courier.getCurrentAvailability()).isEqualTo(CourierAvailability.OFFLINE);
            assertThat(relProfile.getSuspensionUntil()).isAfter(LocalDateTime.now());

            // 7. Settlement Batches & Grouping
            // Reactivate earning to PAYABLE so settlement batch calculator picks it up
            earning.setStatus(CourierEarningStatus.PAYABLE);
            earning.setPayableAt(LocalDateTime.now());
            courierEarningRepository.saveAndFlush(earning);

            CourierSettlementBatch batch = courierSettlementService.calculateBatch(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1)
            );

            assertThat(batch).isNotNull();
            assertThat(batch.getStatus()).isEqualTo(CourierSettlementBatchStatus.CALCULATED);
            assertThat(batch.getTotalJobs()).isEqualTo(1);
            assertThat(batch.getTotalEarningsAmount()).isEqualByComparingTo("1572.50");
            assertThat(batch.getTotalCommissionAmount()).isEqualByComparingTo("277.50");

            List<CourierSettlementLine> lines = settlementLineRepository.findByBatchId(batch.getId());
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getAmount()).isEqualByComparingTo("1572.50");
            assertThat(lines.get(0).getStatus()).isEqualTo(CourierSettlementLineStatus.PAYABLE);

            // Confirm that the earning status transitioned to PAID
            CourierEarning finalEarning = courierEarningRepository.findById(earning.getId()).get();
            assertThat(finalEarning.getStatus()).isEqualTo(CourierEarningStatus.PAID);

            return null;
        });
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("I");
        i.setNif("5000000003");
        i.setTelefoneAutorizacao("+244900000003");
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao inst) {
        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome("UA");
        u.setTipo(TipoUnidadeAtendimento.RESTAURANTE);
        u.setAtiva(true);
        u.setInstituicao(inst);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private User criarUser() {
        User u = new User();
        u.setUsername("user-flow-" + System.nanoTime());
        u.setPassword("x");
        u.setTelefone("+244900" + (int) (Math.random() * 1000000));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private TurnoOperacional criarTurno(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        User u = criarUser();
        TurnoOperacional t = new TurnoOperacional();
        t.setTenant(tenant);
        t.setInstituicao(inst);
        t.setUnidadeAtendimento(ua);
        t.setAbertoPor(u);
        t.setNome("Turno");
        t.setTipo(TurnoOperacionalTipo.DIARIO);
        t.setStatus(TurnoOperacionalStatus.ABERTO);
        t.setAbertoEm(LocalDateTime.now().minusMinutes(5));
        return turnoOperacionalRepository.saveAndFlush(t);
    }

    private SessaoConsumo criarSessao(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        SessaoConsumo s = new SessaoConsumo();
        s.setTenant(tenant);
        s.setInstituicao(inst);
        s.setUnidadeAtendimento(ua);
        s.setStatus(StatusSessaoConsumo.ABERTA);
        s.setModoAnonimo(true);
        s.setTipoSessao(TipoSessao.PRE_PAGO);
        return sessaoConsumoRepository.saveAndFlush(s);
    }
}
