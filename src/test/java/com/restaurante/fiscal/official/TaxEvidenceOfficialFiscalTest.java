package com.restaurante.fiscal.official;

import com.restaurante.fiscal.evidence.service.TaxEvidenceService;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.fiscal.official.repository.TenantOfficialFiscalProfileRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "consuma.tax.enabled=true",
        "consuma.tax.evidence.enabled=true",
        "consuma.fiscal.official.enabled=true"
})
@ActiveProfiles("test")
public class TaxEvidenceOfficialFiscalTest {

    @Autowired private TaxEvidenceService taxEvidenceService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private TurnoOperacionalRepository turnoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;
    @Autowired private TenantOfficialFiscalProfileRepository officialProfileRepository;
    @Autowired private OfficialFiscalSubmissionRepository submissionRepository;
    @Autowired private com.restaurante.fiscal.official.repository.FiscalSigningProfileRepository signingProfileRepository;

    @Test
    void taxEvidenceIncluiEstadoOficialPorDocumento() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        User u = criarUser(ua);
        TurnoOperacional turno = criarTurno(tenant, inst, ua, u);

        TenantFiscalProfile fp = new TenantFiscalProfile();
        fp.setTenant(tenant);
        fp.setStatus(TenantFiscalProfileStatus.ACTIVE);
        fp.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        fp.setFiscalDocumentEnabled(true);
        fiscalProfileRepository.saveAndFlush(fp);

        FiscalSigningProfile sp = new FiscalSigningProfile();
        sp.setTenant(tenant);
        sp.setStatus(FiscalSigningProfileStatus.ACTIVE);
        sp.setKeyProvider(FiscalKeyProvider.MANUAL_PLACEHOLDER);
        sp = signingProfileRepository.saveAndFlush(sp);

        TenantOfficialFiscalProfile op = new TenantOfficialFiscalProfile();
        op.setTenant(tenant);
        op.setStatus(TenantOfficialFiscalProfileStatus.ACTIVE);
        op.setOfficialEnabled(true);
        op.setEnvironment(OfficialFiscalEnvironment.SANDBOX);
        op.setSubmissionMode(OfficialFiscalSubmissionMode.MANUAL);
        op.setAuthority(FiscalAuthority.AGT_AO);
        op.setSigningProfile(sp);
        officialProfileRepository.saveAndFlush(op);

        FiscalDocument doc = new FiscalDocument();
        doc.setTenant(tenant);
        doc.setInstituicao(inst);
        doc.setUnidadeAtendimento(ua);
        doc.setTurnoOperacional(turno);
        doc.setDocumentType(FiscalDocumentType.INTERNAL_RECEIPT);
        doc.setStatus(FiscalDocumentStatus.ISSUED);
        doc.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        doc.setSeries("A");
        doc.setDocumentNumber("INT-2026-000800");
        doc.setIssuedAt(LocalDateTime.now().minusMinutes(3));
        doc.setSubtotalAmount(new BigDecimal("100.00"));
        doc.setTaxableAmount(new BigDecimal("100.00"));
        doc.setExemptAmount(BigDecimal.ZERO);
        doc.setTaxAmount(new BigDecimal("14.00"));
        doc.setTotalAmount(new BigDecimal("114.00"));
        doc.setCurrency("AOA");
        doc.setSource(FiscalDocumentSource.SYSTEM);
        doc = fiscalDocumentRepository.saveAndFlush(doc);

        OfficialFiscalSubmission sub = new OfficialFiscalSubmission();
        sub.setTenant(tenant);
        sub.setFiscalDocument(doc);
        sub.setDocumentType(doc.getDocumentType());
        sub.setAuthority(FiscalAuthority.AGT_AO);
        sub.setEnvironment(OfficialFiscalEnvironment.SANDBOX);
        sub.setIdempotencyKey("tenant:" + tenant.getId() + ":fiscalDocument:" + doc.getId() + ":official-submission:v1");
        sub.setStatus(OfficialFiscalSubmissionStatus.ACCEPTED);
        sub.setRequestId("SIM-REQ");
        sub.setPayloadHash("ph");
        sub.setSignedPayloadHash("sph");
        sub.setAcceptedAt(LocalDateTime.now());
        submissionRepository.saveAndFlush(sub);

        var evidence = taxEvidenceService.buildForTurno(tenant.getId(), turno.getId());
        assertThat(evidence.getOfficialFiscalEnabled()).isTrue();
        assertThat(evidence.getTotalOfficialSubmissions()).isGreaterThanOrEqualTo(1);
        assertThat(evidence.getDocuments()).isNotEmpty();
        assertThat(evidence.getDocuments().get(0).getOfficialSubmissionStatus()).isNotNull();
    }

    private Tenant criarTenant() {
        long suffix = System.nanoTime();
        Tenant t = new Tenant();
        t.setNome("Tenant TaxEvidence Official " + suffix);
        t.setSlug("tenant-taxev-off-" + suffix);
        t.setTenantCode("TEXO" + (suffix % 1_000_000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        long suffix = System.nanoTime();
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst " + suffix);
        i.setSigla("IX" + (suffix % 1_000_000));
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
                .username("u-" + suffix)
                .password("x")
                .email("u-" + suffix + "@x.com")
                .nomeCompleto("User " + suffix)
                .telefone("+244915" + (suffix % 1_000_000))
                .unidadeAtendimento(ua)
                .roles(java.util.Set.of(Role.ROLE_GERENTE))
                .ativo(true)
                .build();
        return userRepository.saveAndFlush(u);
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
        t.setAbertoEm(LocalDateTime.now().minusMinutes(10));
        return turnoRepository.saveAndFlush(t);
    }
}
