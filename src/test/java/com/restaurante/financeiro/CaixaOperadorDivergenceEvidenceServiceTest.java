package com.restaurante.financeiro;

import com.restaurante.financeiro.caixa.divergence.evidence.service.CaixaOperadorDivergenceEvidenceService;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashDivergenceEvidenceSectionDTO;
import com.restaurante.model.entity.CaixaOperadorAdjustment;
import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;
import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import com.restaurante.model.enums.CaixaOperadorDivergenceSeverity;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CaixaOperadorDivergenceEvidenceServiceTest {

    @Autowired CaixaOperadorDivergenceEvidenceService evidenceService;
    @Autowired CaixaOperadorDivergenceRepository divergenceRepository;
    @Autowired CaixaOperadorAdjustmentRepository adjustmentRepository;
    @Autowired CaixaOperadorSessionRepository caixaRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;

    @Test
    @Transactional
    void buildForTurno_counts_and_hashes_are_deterministic_and_sensitive_to_state() {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Div Ev");
        tenant.setSlug("tenant-div-ev-" + System.nanoTime());
        tenant.setTenantCode("DVE" + (System.nanoTime() % 10000));
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.save(tenant);

        Instituicao inst = Instituicao.builder()
                .tenant(tenant)
                .nome("Inst Div Ev")
                .sigla("DE" + (System.nanoTime() % 1000))
                .nif("NIF-" + (System.nanoTime() % 1_000_000))
                .telefoneAutorizacao("+244900000002")
                .ativa(true)
                .build();
        inst = instituicaoRepository.save(inst);

        UnidadeAtendimento ua = UnidadeAtendimento.builder()
                .nome("UA Div Ev")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .ativa(true)
                .instituicao(inst)
                .build();
        ua = unidadeAtendimentoRepository.save(ua);

        User operador = User.builder()
                .username("op-div-ev-" + System.nanoTime())
                .password("x")
                .telefone("+244913" + (System.nanoTime() % 1_000_000))
                .roles(Set.of(Role.ROLE_GERENTE))
                .unidadeAtendimento(ua)
                .ativo(true)
                .build();
        operador = userRepository.save(operador);

        DispositivoOperacional dev = new DispositivoOperacional();
        dev.setTenant(tenant);
        dev.setInstituicao(inst);
        dev.setUnidadeAtendimento(ua);
        dev.setTipo(DispositivoTipo.POS);
        dev.setStatus(DispositivoStatus.ATIVO);
        dev.setCodigo("POS-DVE-" + (System.nanoTime() % 1_000_000));
        dev.setNome("POS DVE");
        dev = dispositivoOperacionalRepository.save(dev);

        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(inst);
        turno.setUnidadeAtendimento(ua);
        turno.setAbertoPor(operador);
        turno.setStatus(TurnoOperacionalStatus.FECHADO);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno DVE");
        turno.setAbertoEm(LocalDateTime.now().minusHours(2));
        turno.setFechadoEm(LocalDateTime.now().minusHours(1));
        turno = turnoOperacionalRepository.save(turno);

        CaixaOperadorSession caixa = new CaixaOperadorSession();
        caixa.setTenant(tenant);
        caixa.setInstituicao(inst);
        caixa.setUnidadeAtendimento(ua);
        caixa.setTurnoOperacional(turno);
        caixa.setDispositivoOperacional(dev);
        caixa.setOperador(operador);
        caixa.setOpenedBy(operador);
        caixa.setStatus(CaixaOperadorSessionStatus.DISPUTED);
        caixa.setOpenedAt(LocalDateTime.now().minusHours(2));
        caixa.setClosedAt(LocalDateTime.now().minusHours(1));
        caixa = caixaRepository.save(caixa);

        CaixaOperadorDivergence div = new CaixaOperadorDivergence();
        div.setTenant(tenant);
        div.setUnidadeAtendimento(ua);
        div.setTurnoOperacional(turno);
        div.setCaixaOperadorSession(caixa);
        div.setDispositivoOperacional(dev);
        div.setOperador(operador);
        div.setStatus(CaixaOperadorDivergenceStatus.SUBMITTED);
        div.setType(CaixaOperadorDivergenceType.CASH_SHORTAGE);
        div.setSeverity(CaixaOperadorDivergenceSeverity.LOW);
        div.setPaymentMethod(CaixaOperadorDivergencePaymentMethod.CASH);
        div.setExpectedAmount(new BigDecimal("10.00"));
        div.setDeclaredAmount(new BigDecimal("9.00"));
        div.setDifferenceAmount(new BigDecimal("-1.00"));
        div.setAbsoluteDifferenceAmount(new BigDecimal("1.00"));
        div.setSubmittedAt(LocalDateTime.now().minusMinutes(10));
        div = divergenceRepository.save(div);

        CaixaOperadorAdjustment adj = new CaixaOperadorAdjustment();
        adj.setTenant(tenant);
        adj.setDivergence(div);
        adj.setCaixaOperadorSession(caixa);
        adj.setAdjustmentType(CaixaOperadorAdjustmentType.ACCEPTED_LOSS);
        adj.setPaymentMethod(div.getPaymentMethod());
        adj.setAmount(new BigDecimal("1.00"));
        adj.setDirection(CaixaOperadorAdjustmentDirection.NO_LEDGER_IMPACT);
        adj.setStatus(CaixaOperadorAdjustmentStatus.APPROVED);
        adj.setApprovedBy(operador);
        adj.setApprovedAt(LocalDateTime.now().minusMinutes(5));
        adj = adjustmentRepository.save(adj);

        OperatorCashDivergenceEvidenceSectionDTO sec1 = evidenceService.buildForTurno(tenant.getId(), turno.getId());
        assertThat(sec1.getTotalDivergences()).isEqualTo(1);
        assertThat(sec1.getSubmittedDivergences()).isEqualTo(1);
        assertThat(sec1.getTotalAdjustments()).isEqualTo(1);
        assertThat(sec1.getApprovedAdjustments()).isEqualTo(1);
        assertThat(sec1.getTotalApprovedAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(sec1.getDivergenceItems()).hasSize(1);
        assertThat(sec1.getDivergenceItems().get(0).getDivergenceHash()).isNotBlank();
        assertThat(sec1.getAdjustmentItems()).hasSize(1);
        assertThat(sec1.getAdjustmentItems().get(0).getAdjustmentHash()).isNotBlank();

        // Mutate a relevant field and ensure hash changes
        String oldHash = sec1.getDivergenceItems().get(0).getDivergenceHash();
        div.setReviewNotes("Nota de revisão " + System.nanoTime());
        div.setReviewedAt(LocalDateTime.now());
        divergenceRepository.save(div);

        OperatorCashDivergenceEvidenceSectionDTO sec2 = evidenceService.buildForTurno(tenant.getId(), turno.getId());
        assertThat(sec2.getDivergenceItems().get(0).getDivergenceHash()).isNotEqualTo(oldHash);
    }
}

