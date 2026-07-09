package com.restaurante.fiscal.corrections;

import com.restaurante.fiscal.corrections.service.FiscalAdjustmentAssessmentService;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
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
public class FiscalAdjustmentAssessmentServiceTest {

    @Autowired private FiscalAdjustmentAssessmentService service;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CaixaOperadorSessionRepository caixaOperadorSessionRepository;
    @Autowired private CaixaOperadorDivergenceRepository divergenceRepository;
    @Autowired private CaixaOperadorAdjustmentRepository adjustmentRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;

    @Test
    void criaAssessmentParaAdjustmentAprovado_adminCorrectionDecrease_viraCreditNote() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        User operador = criarUser(ua);
        DispositivoOperacional device = criarDevice(tenant, inst, ua);
        CaixaOperadorSession caixa = criarCaixa(tenant, inst, ua, device, operador);

        CaixaOperadorDivergence div = new CaixaOperadorDivergence();
        div.setTenant(tenant);
        div.setUnidadeAtendimento(ua);
        div.setCaixaOperadorSession(caixa);
        div.setDispositivoOperacional(device);
        div.setOperador(operador);
        div.setStatus(CaixaOperadorDivergenceStatus.APPROVED);
        div.setType(CaixaOperadorDivergenceType.CASH_SHORTAGE);
        div.setSeverity(CaixaOperadorDivergenceSeverity.LOW);
        div.setPaymentMethod(CaixaOperadorDivergencePaymentMethod.CASH);
        div.setExpectedAmount(new BigDecimal("100.00"));
        div.setDeclaredAmount(new BigDecimal("90.00"));
        div.setDifferenceAmount(new BigDecimal("-10.00"));
        div.setAbsoluteDifferenceAmount(new BigDecimal("10.00"));
        div = divergenceRepository.saveAndFlush(div);

        CaixaOperadorAdjustment adj = new CaixaOperadorAdjustment();
        adj.setTenant(tenant);
        adj.setDivergence(div);
        adj.setCaixaOperadorSession(caixa);
        adj.setAdjustmentType(CaixaOperadorAdjustmentType.ADMIN_CORRECTION);
        adj.setPaymentMethod(CaixaOperadorDivergencePaymentMethod.CASH);
        adj.setAmount(new BigDecimal("10.00"));
        adj.setDirection(CaixaOperadorAdjustmentDirection.DECREASE_DECLARED);
        adj.setStatus(CaixaOperadorAdjustmentStatus.APPROVED);
        adj.setApprovedAt(LocalDateTime.now());
        adj = adjustmentRepository.saveAndFlush(adj);

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setFiscalDocumentEnabled(true);
        fiscalProfileRepository.saveAndFlush(profile);

        FiscalAdjustmentAssessment a = service.createAssessmentForApprovedAdjustment(tenant.getId(), adj.getId());
        assertThat(a).isNotNull();
        assertThat(a.getStatus()).isEqualTo(FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE);
        assertThat(a.getImpactType()).isEqualTo(FiscalAdjustmentImpactType.REDUCE_TAXABLE_AMOUNT);
    }

    private Tenant criarTenant() {
        long suffix = System.nanoTime();
        Tenant t = new Tenant();
        t.setNome("Tenant Fiscal Corrections " + suffix);
        t.setSlug("tenant-fiscal-corr-" + suffix);
        t.setTenantCode("FISCC" + (suffix % 1_000_000));
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
                .email("op-" + suffix + "@x.com")
                .nomeCompleto("Operador " + suffix)
                .telefone("+244911" + (suffix % 1_000_000))
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
        d.setNome("POS Fiscal Corrections");
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
        c.setStatus(CaixaOperadorSessionStatus.OPEN);
        c.setOpenedAt(LocalDateTime.now().minusMinutes(1));
        c.setCurrency("AOA");
        c.setExpectedCashAmount(BigDecimal.ZERO);
        c.setExpectedTpaAmount(BigDecimal.ZERO);
        c.setExpectedManualTotalAmount(BigDecimal.ZERO);
        c.setExpectedAppyPayAmount(BigDecimal.ZERO);
        return caixaOperadorSessionRepository.saveAndFlush(c);
    }
}
