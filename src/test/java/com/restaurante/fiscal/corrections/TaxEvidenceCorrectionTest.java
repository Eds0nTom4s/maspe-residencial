package com.restaurante.fiscal.corrections;

import com.restaurante.fiscal.evidence.service.TaxEvidenceService;
import com.restaurante.fiscal.repository.FiscalAdjustmentAssessmentRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class TaxEvidenceCorrectionTest {

    @Autowired private TaxEvidenceService taxEvidenceService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private TurnoOperacionalRepository turnoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;
    @Autowired private FiscalDocumentLineRepository fiscalDocumentLineRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;
    @Autowired private FiscalAdjustmentAssessmentRepository assessmentRepository;
    @Autowired private CaixaOperadorSessionRepository caixaRepository;
    @Autowired private CaixaOperadorDivergenceRepository divergenceRepository;
    @Autowired private CaixaOperadorAdjustmentRepository adjustmentRepository;

    @Test
    void taxEvidenceIncluiAssessmentsECorrecoes() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        User u = criarUser(ua);
        DispositivoOperacional device = criarDevice(tenant, inst, ua);
        TurnoOperacional turno = criarTurno(tenant, inst, ua, u);

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setFiscalDocumentEnabled(true);
        fiscalProfileRepository.saveAndFlush(profile);

        FiscalDocument original = criarDocumento(tenant, inst, ua, turno, FiscalDocumentType.INTERNAL_RECEIPT, "INT-2026-000010");

        CaixaOperadorSession caixa = criarCaixa(tenant, inst, ua, device, u);
        CaixaOperadorDivergence div = criarDivergencia(tenant, ua, caixa, u, turno);
        CaixaOperadorAdjustment adj = criarAdjustment(tenant, caixa, div, u);

        FiscalAdjustmentAssessment assessment = new FiscalAdjustmentAssessment();
        assessment.setTenant(tenant);
        assessment.setAdjustment(adj);
        assessment.setTurnoOperacional(turno);
        assessment.setUnidadeAtendimento(ua);
        assessment.setOriginalFiscalDocument(original);
        assessment.setStatus(FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE);
        assessment.setImpactType(FiscalAdjustmentImpactType.REDUCE_TAXABLE_AMOUNT);
        assessment.setAssessedAt(LocalDateTime.now());
        assessment = assessmentRepository.saveAndFlush(assessment);
        Long assessmentId = assessment.getId();

        FiscalDocument correction = criarDocumento(tenant, inst, ua, turno, FiscalDocumentType.INTERNAL_CREDIT_NOTE, "INT-2026-000011");
        correction.setOriginalFiscalDocument(original);
        correction.setFiscalAdjustmentAssessment(assessment);
        correction.setCorrectionSource(FiscalCorrectionSource.CAIXA_OPERADOR_ADJUSTMENT);
        correction.setCorrectionReason("Teste");
        fiscalDocumentRepository.saveAndFlush(correction);

        var evidence = taxEvidenceService.buildForTurno(tenant.getId(), turno.getId());

        assertThat(evidence.getTotalCorrectionDocuments()).isNotNull();
        assertThat(evidence.getTotalCorrectionDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(evidence.getAssessments()).isNotNull();
        assertThat(evidence.getAssessments().stream().anyMatch(a -> a.getAssessmentId().equals(assessmentId))).isTrue();
        assertThat(evidence.getCorrectionDocuments()).isNotNull();
        assertThat(evidence.getCorrectionDocuments().stream().anyMatch(d -> d.getCorrectionDocumentId().equals(correction.getId()))).isTrue();
        assertThat(evidence.getWarnings()).contains("FISCAL_ASSESSMENT_REQUIRES_CREDIT_NOTE");
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

    private CaixaOperadorDivergence criarDivergencia(Tenant tenant, UnidadeAtendimento ua, CaixaOperadorSession caixa, User operador, TurnoOperacional turno) {
        CaixaOperadorDivergence d = new CaixaOperadorDivergence();
        d.setTenant(tenant);
        d.setUnidadeAtendimento(ua);
        d.setTurnoOperacional(turno);
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

    private FiscalDocument criarDocumento(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, TurnoOperacional turno, FiscalDocumentType type, String number) {
        FiscalDocument doc = new FiscalDocument();
        doc.setTenant(tenant);
        doc.setInstituicao(inst);
        doc.setUnidadeAtendimento(ua);
        doc.setTurnoOperacional(turno);
        doc.setDocumentType(type);
        doc.setStatus(FiscalDocumentStatus.ISSUED);
        doc.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        doc.setSeries("A");
        doc.setDocumentNumber(number);
        doc.setIssuedAt(LocalDateTime.now().minusMinutes(1));
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

        return doc;
    }

    private Tenant criarTenant() {
        long suffix = System.nanoTime();
        Tenant t = new Tenant();
        t.setNome("Tenant Evidence Corr " + suffix);
        t.setSlug("tenant-evidence-corr-" + suffix);
        t.setTenantCode("TECORR" + (suffix % 1_000_000));
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

    private User criarUser(UnidadeAtendimento ua) {
        long suffix = System.nanoTime();
        User u = User.builder()
                .username("op-" + suffix)
                .password("x")
                .email("u-" + suffix + "@x.com")
                .nomeCompleto("Operador " + suffix)
                .telefone("+244914" + (suffix % 1_000_000))
                .unidadeAtendimento(ua)
                .roles(java.util.Set.of(Role.ROLE_GERENTE))
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
        d.setNome("POS Evidence Corr");
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private TurnoOperacional criarTurno(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, User u) {
        TurnoOperacional t = new TurnoOperacional();
        t.setTenant(tenant);
        t.setInstituicao(inst);
        t.setUnidadeAtendimento(ua);
        t.setAbertoPor(u);
        t.setNome("Turno");
        t.setTipo(TurnoOperacionalTipo.DIARIO);
        t.setStatus(TurnoOperacionalStatus.ABERTO);
        t.setAbertoEm(LocalDateTime.now().minusMinutes(2));
        return turnoRepository.saveAndFlush(t);
    }
}
