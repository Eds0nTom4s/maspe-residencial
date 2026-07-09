package com.restaurante.fiscal.corrections;

import com.restaurante.dto.request.FiscalAssessmentDecisionRequest;
import com.restaurante.dto.request.FiscalCorrectionIssueRequest;
import com.restaurante.fiscal.corrections.service.FiscalCorrectionAdminService;
import com.restaurante.fiscal.repository.FiscalAdjustmentAssessmentRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class FiscalCorrectionDocumentServiceTest {

    @Autowired private FiscalCorrectionAdminService adminService;
    @Autowired private FiscalAdjustmentAssessmentRepository assessmentRepository;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;
    @Autowired private FiscalDocumentLineRepository fiscalDocumentLineRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CaixaOperadorSessionRepository caixaRepository;
    @Autowired private CaixaOperadorDivergenceRepository divergenceRepository;
    @Autowired private CaixaOperadorAdjustmentRepository adjustmentRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private TenantTaxPolicyRepository taxPolicyRepository;

    @AfterEach
    void cleanupTenantCtx() {
        TenantContextHolder.clear();
    }

    @Test
    void emiteNotaCreditoInterna_semAlterarDocumentoOriginal() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        User finance = criarUser(ua, "finance@x.com");
        DispositivoOperacional device = criarDevice(tenant, inst, ua);
        TenantContextHolder.set(new TenantContext(
                tenant.getId(), null, finance.getId(), Set.of(TenantUserRole.TENANT_FINANCE.name()), TenantResolutionSource.LEGACY_NONE, true, false
        ));

        seedFiscalProfileAndPolicy(tenant);

        FiscalDocument original = criarDocumentoOriginal(tenant, inst, ua);

        CaixaOperadorSession caixa = criarCaixa(tenant, inst, ua, device, finance);
        CaixaOperadorDivergence div = criarDivergencia(tenant, ua, caixa, finance);
        CaixaOperadorAdjustment adj = criarAdjustment(tenant, caixa, div, finance);

        FiscalAdjustmentAssessment a = new FiscalAdjustmentAssessment();
        a.setTenant(tenant);
        a.setAdjustment(adj);
        a.setDivergence(div);
        a.setCaixaOperadorSession(caixa);
        a.setTurnoOperacional(div.getTurnoOperacional());
        a.setUnidadeAtendimento(ua);
        a.setStatus(FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE);
        a.setImpactType(FiscalAdjustmentImpactType.REDUCE_TAXABLE_AMOUNT);
        a.setOriginalFiscalDocument(original);
        a = assessmentRepository.saveAndFlush(a);

        FiscalCorrectionIssueRequest req = new FiscalCorrectionIssueRequest();
        req.setAmount(new BigDecimal("114.00")); // gross
        req.setReason("Desconto aprovado após emissão");
        req.setLineMode(FiscalCorrectionLineMode.SINGLE_ADJUSTMENT_LINE);
        req.setOriginalFiscalDocumentId(original.getId());

        var resp = adminService.issueCreditNote(a.getId(), req);
        assertThat(resp.getCorrectionFiscalDocumentId()).isNotNull();

        FiscalDocument correction = fiscalDocumentRepository.findById(resp.getCorrectionFiscalDocumentId()).orElseThrow();
        assertThat(correction.getDocumentType()).isEqualTo(FiscalDocumentType.INTERNAL_CREDIT_NOTE);
        assertThat(correction.getOriginalFiscalDocument().getId()).isEqualTo(original.getId());

        // original intacto
        FiscalDocument originalReload = fiscalDocumentRepository.findById(original.getId()).orElseThrow();
        assertThat(originalReload.getDocumentType()).isEqualTo(FiscalDocumentType.INTERNAL_RECEIPT);
        assertThat(originalReload.getTotalAmount()).isEqualByComparingTo(new BigDecimal("114.00"));
    }

    private void seedFiscalProfileAndPolicy(Tenant tenant) {
        TaxRate rate = new TaxRate();
        rate.setCountryCode("AO");
        rate.setTaxType(TaxType.VAT);
        rate.setCode("AO_VAT_STANDARD_14");
        rate.setName("IVA Geral 14%");
        rate.setRate(new BigDecimal("14.00"));
        rate.setStatus(TaxRateStatus.ACTIVE);
        rate.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        rate = taxRateRepository.saveAndFlush(rate);

        TenantTaxPolicy policy = new TenantTaxPolicy();
        policy.setTenant(tenant);
        policy.setName("Default VAT");
        policy.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        policy.setDefaultTaxRate(rate);
        policy.setPricesIncludeTax(false);
        policy.setAllowTaxExemptItems(true);
        policy.setRequireTaxDocumentOnPayment(false);
        policy.setStatus(TenantTaxPolicyStatus.ACTIVE);
        policy.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        policy = taxPolicyRepository.saveAndFlush(policy);

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setFiscalDocumentEnabled(true);
        profile.setDefaultTaxPolicy(policy);
        fiscalProfileRepository.saveAndFlush(profile);
    }

    private FiscalDocument criarDocumentoOriginal(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        FiscalDocument doc = new FiscalDocument();
        doc.setTenant(tenant);
        doc.setInstituicao(inst);
        doc.setUnidadeAtendimento(ua);
        doc.setDocumentType(FiscalDocumentType.INTERNAL_RECEIPT);
        doc.setStatus(FiscalDocumentStatus.ISSUED);
        doc.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        doc.setSeries("A");
        doc.setDocumentNumber("INT-2026-000001");
        doc.setIssuedAt(LocalDateTime.now().minusMinutes(10));
        doc.setSubtotalAmount(new BigDecimal("100.00"));
        doc.setTaxableAmount(new BigDecimal("100.00"));
        doc.setExemptAmount(BigDecimal.ZERO);
        doc.setTaxAmount(new BigDecimal("14.00"));
        doc.setTotalAmount(new BigDecimal("114.00"));
        doc.setCurrency("AOA");
        doc.setSource(FiscalDocumentSource.SYSTEM);
        doc = fiscalDocumentRepository.saveAndFlush(doc);

        FiscalDocumentLine line = new FiscalDocumentLine();
        line.setFiscalDocument(doc);
        line.setTenant(tenant);
        line.setDescription("Item");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("114.00"));
        line.setNetAmount(new BigDecimal("100.00"));
        line.setTaxRateCode("AO_VAT_STANDARD_14");
        line.setTaxRateValue(new BigDecimal("14.00"));
        line.setTaxAmount(new BigDecimal("14.00"));
        line.setGrossAmount(new BigDecimal("114.00"));
        line.setTaxCategory(TaxCategory.STANDARD);
        fiscalDocumentLineRepository.saveAndFlush(line);

        doc.setLines(java.util.List.of(line));
        return doc;
    }

    private Tenant criarTenant() {
        long suffix = System.nanoTime();
        Tenant t = new Tenant();
        t.setNome("Tenant Corr " + suffix);
        t.setSlug("tenant-corr-" + suffix);
        t.setTenantCode("TCORR" + (suffix % 1_000_000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        long suffix = System.nanoTime();
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst " + suffix);
        i.setSigla("I" + (suffix % 1_000_000));
        i.setNif("5000000" + (suffix % 1_000_000));
        i.setTelefoneAutorizacao("+244900" + (suffix % 1_000_000));
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

    private User criarUser(UnidadeAtendimento ua, String email) {
        long suffix = System.nanoTime();
        User u = User.builder()
                .username("finance-" + suffix)
                .password("x")
                .email(email)
                .nomeCompleto("Finance " + suffix)
                .telefone("+244912" + (suffix % 1_000_000))
                .unidadeAtendimento(ua)
                .roles(Set.of(Role.ROLE_GERENTE))
                .ativo(true)
                .build();
        return userRepository.saveAndFlush(u);
    }

    private DispositivoOperacional criarDevice(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        long suffix = System.nanoTime();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(OperationalDeviceType.POS_CAIXA);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setCodigo("POS-" + (suffix % 1_000_000));
        d.setNome("POS Corr");
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private CaixaOperadorSession criarCaixa(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, DispositivoOperacional device, User operador) {
        CaixaOperadorSession c = new CaixaOperadorSession();
        c.setTenant(tenant);
        c.setInstituicao(inst);
        c.setUnidadeAtendimento(ua);
        c.setDispositivoOperacional(device);
        c.setOperador(operador);
        c.setOpenedBy(operador);
        c.setStatus(CaixaOperadorSessionStatus.CLOSED);
        c.setOpenedAt(LocalDateTime.now().minusMinutes(20));
        c.setClosedAt(LocalDateTime.now().minusMinutes(5));
        c.setCurrency("AOA");
        c.setExpectedCashAmount(BigDecimal.ZERO);
        c.setExpectedTpaAmount(BigDecimal.ZERO);
        c.setExpectedManualTotalAmount(BigDecimal.ZERO);
        c.setExpectedAppyPayAmount(BigDecimal.ZERO);
        return caixaRepository.saveAndFlush(c);
    }

    private CaixaOperadorDivergence criarDivergencia(Tenant tenant, UnidadeAtendimento ua, CaixaOperadorSession caixa, User operador) {
        CaixaOperadorDivergence d = new CaixaOperadorDivergence();
        d.setTenant(tenant);
        d.setUnidadeAtendimento(ua);
        d.setCaixaOperadorSession(caixa);
        d.setDispositivoOperacional(caixa.getDispositivoOperacional());
        d.setOperador(operador);
        d.setStatus(CaixaOperadorDivergenceStatus.APPROVED);
        d.setType(CaixaOperadorDivergenceType.CASH_SHORTAGE);
        d.setSeverity(CaixaOperadorDivergenceSeverity.LOW);
        d.setPaymentMethod(CaixaOperadorDivergencePaymentMethod.CASH);
        d.setExpectedAmount(new BigDecimal("100.00"));
        d.setDeclaredAmount(new BigDecimal("90.00"));
        d.setDifferenceAmount(new BigDecimal("-10.00"));
        d.setAbsoluteDifferenceAmount(new BigDecimal("10.00"));
        return divergenceRepository.saveAndFlush(d);
    }

    private CaixaOperadorAdjustment criarAdjustment(Tenant tenant, CaixaOperadorSession caixa, CaixaOperadorDivergence div, User finance) {
        CaixaOperadorAdjustment adj = new CaixaOperadorAdjustment();
        adj.setTenant(tenant);
        adj.setDivergence(div);
        adj.setCaixaOperadorSession(caixa);
        adj.setAdjustmentType(CaixaOperadorAdjustmentType.ADMIN_CORRECTION);
        adj.setPaymentMethod(CaixaOperadorDivergencePaymentMethod.CASH);
        adj.setAmount(new BigDecimal("10.00"));
        adj.setDirection(CaixaOperadorAdjustmentDirection.DECREASE_DECLARED);
        adj.setStatus(CaixaOperadorAdjustmentStatus.APPROVED);
        adj.setApprovedBy(finance);
        adj.setApprovedAt(LocalDateTime.now());
        return adjustmentRepository.saveAndFlush(adj);
    }
}
