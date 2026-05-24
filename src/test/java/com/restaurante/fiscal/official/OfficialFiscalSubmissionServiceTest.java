package com.restaurante.fiscal.official;

import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.fiscal.official.repository.TenantOfficialFiscalProfileRepository;
import com.restaurante.fiscal.official.service.OfficialFiscalSubmissionService;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalSigningProfile;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "consuma.fiscal.official.enabled=true",
        "consuma.fiscal.official.simulation-enabled=true",
        "consuma.fiscal.official.worker-enabled=false"
})
@ActiveProfiles("test")
public class OfficialFiscalSubmissionServiceTest {

    @Autowired private OfficialFiscalSubmissionService service;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private FiscalDocumentRepository fiscalDocumentRepository;
    @Autowired private TenantOfficialFiscalProfileRepository profileRepository;
    @Autowired private OfficialFiscalSubmissionRepository submissionRepository;
    @Autowired private com.restaurante.fiscal.official.repository.FiscalSigningProfileRepository signingProfileRepository;

    @Test
    void criaSubmissaoEPermiteSimularAceite() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);

        FiscalSigningProfile sp = new FiscalSigningProfile();
        sp.setTenant(tenant);
        sp.setStatus(FiscalSigningProfileStatus.ACTIVE);
        sp.setKeyProvider(FiscalKeyProvider.MANUAL_PLACEHOLDER);
        sp = signingProfileRepository.saveAndFlush(sp);

        TenantOfficialFiscalProfile p = new TenantOfficialFiscalProfile();
        p.setTenant(tenant);
        p.setStatus(TenantOfficialFiscalProfileStatus.ACTIVE);
        p.setOfficialEnabled(true);
        p.setEnvironment(OfficialFiscalEnvironment.SANDBOX);
        p.setSubmissionMode(OfficialFiscalSubmissionMode.MANUAL);
        p.setAuthority(FiscalAuthority.AGT_AO);
        p.setSigningProfile(sp);
        profileRepository.saveAndFlush(p);

        FiscalDocument d = criarDocumento(tenant, inst, ua, "INT-2026-000900");

        var s1 = service.createForDocument(tenant.getId(), d.getId());
        var s2 = service.createForDocument(tenant.getId(), d.getId());
        assertThat(s1.getId()).isEqualTo(s2.getId());

        var sub = service.simulateSubmit(tenant.getId(), s1.getId());
        assertThat(sub.getRequestId()).isNotNull();
        var acc = service.simulateAccept(tenant.getId(), sub.getId(), "ACCEPTED", "ok");
        assertThat(acc.getStatus()).isEqualTo(OfficialFiscalSubmissionStatus.ACCEPTED);
        assertThat(submissionRepository.findByTenantIdAndFiscalDocumentId(tenant.getId(), d.getId())).isPresent();
    }

    private Tenant criarTenant() {
        long suffix = System.nanoTime();
        Tenant t = new Tenant();
        t.setNome("Tenant Official " + suffix);
        t.setSlug("tenant-official-" + suffix);
        t.setTenantCode("TOFF" + (suffix % 1_000_000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        long suffix = System.nanoTime();
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst " + suffix);
        i.setSigla("IO" + (suffix % 1_000_000));
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

    private FiscalDocument criarDocumento(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, String number) {
        FiscalDocument doc = new FiscalDocument();
        doc.setTenant(tenant);
        doc.setInstituicao(inst);
        doc.setUnidadeAtendimento(ua);
        doc.setDocumentType(FiscalDocumentType.INTERNAL_RECEIPT);
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
        return fiscalDocumentRepository.saveAndFlush(doc);
    }
}
