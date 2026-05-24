package com.restaurante.financeiro;

import com.restaurante.financeiro.caixa.evidence.service.CaixaOperadorEvidenceService;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashEvidenceSectionDTO;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
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
class CaixaOperadorEvidenceServiceTest {

    @Autowired CaixaOperadorEvidenceService evidenceService;
    @Autowired CaixaOperadorSessionRepository caixaRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;

    @Test
    @Transactional
    void buildEvidenceSection_sums_amounts_and_generates_hash_and_warnings() {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Evidence");
        tenant.setSlug("tenant-evidence-" + System.nanoTime());
        tenant.setTenantCode("EVD" + (System.nanoTime() % 10000));
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.save(tenant);

        Instituicao inst = Instituicao.builder()
                .tenant(tenant)
                .nome("Inst Evidence")
                .sigla("IE" + (System.nanoTime() % 1000))
                .nif("NIF-" + (System.nanoTime() % 1_000_000))
                .telefoneAutorizacao("+244900000000")
                .ativa(true)
                .build();
        inst = instituicaoRepository.save(inst);

        UnidadeAtendimento ua = UnidadeAtendimento.builder()
                .nome("UA Evidence")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .ativa(true)
                .instituicao(inst)
                .build();
        ua = unidadeAtendimentoRepository.save(ua);

        User operador = User.builder()
                .username("op-" + System.nanoTime())
                .password("x")
                .telefone("+244911" + (System.nanoTime() % 1_000_000))
                .roles(Set.of(Role.ROLE_GERENTE))
                .unidadeAtendimento(ua)
                .ativo(true)
                .build();
        operador = userRepository.save(operador);

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setCodigo("POS-" + (System.nanoTime() % 1_000_000));
        d.setNome("POS Evidence");
        d = dispositivoOperacionalRepository.save(d);

        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(inst);
        turno.setUnidadeAtendimento(ua);
        turno.setAbertoPor(operador);
        turno.setStatus(TurnoOperacionalStatus.FECHADO);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno Evidence");
        turno.setAbertoEm(LocalDateTime.now().minusHours(2));
        turno.setFechadoEm(LocalDateTime.now().minusHours(1));
        turno = turnoOperacionalRepository.save(turno);

        CaixaOperadorSession c1 = new CaixaOperadorSession();
        c1.setTenant(tenant);
        c1.setInstituicao(inst);
        c1.setUnidadeAtendimento(ua);
        c1.setTurnoOperacional(turno);
        c1.setDispositivoOperacional(d);
        c1.setOperador(operador);
        c1.setOpenedBy(operador);
        c1.setStatus(CaixaOperadorSessionStatus.CLOSED);
        c1.setOpenedAt(LocalDateTime.now().minusHours(2));
        c1.setClosedAt(LocalDateTime.now().minusHours(1));
        c1.setExpectedCashAmount(new BigDecimal("10.00"));
        c1.setDeclaredCashAmount(new BigDecimal("12.00"));
        c1.setCashDifferenceAmount(new BigDecimal("2.00"));
        c1.setExpectedTpaAmount(new BigDecimal("5.00"));
        c1.setDeclaredTpaAmount(new BigDecimal("5.00"));
        c1.setTpaDifferenceAmount(new BigDecimal("0.00"));
        c1.setExpectedManualTotalAmount(new BigDecimal("15.00"));
        c1.setDeclaredManualTotalAmount(new BigDecimal("17.00"));
        c1.setManualDifferenceAmount(new BigDecimal("2.00"));
        c1 = caixaRepository.save(c1);

        CaixaOperadorSession c2 = new CaixaOperadorSession();
        c2.setTenant(tenant);
        c2.setInstituicao(inst);
        c2.setUnidadeAtendimento(ua);
        c2.setTurnoOperacional(turno);
        c2.setDispositivoOperacional(d);
        c2.setOperador(operador);
        c2.setOpenedBy(operador);
        c2.setStatus(CaixaOperadorSessionStatus.OPEN);
        c2.setOpenedAt(LocalDateTime.now().minusMinutes(30));
        c2.setExpectedCashAmount(new BigDecimal("0.00"));
        c2.setExpectedTpaAmount(new BigDecimal("0.00"));
        c2.setExpectedManualTotalAmount(new BigDecimal("0.00"));
        c2 = caixaRepository.save(c2);

        OperatorCashEvidenceSectionDTO sec = evidenceService.buildForTurno(
                tenant.getId(),
                inst.getId(),
                ua.getId(),
                turno.getId(),
                turno.getAbertoEm(),
                turno.getFechadoEm()
        );

        assertThat(sec.getTotalCashSessions()).isEqualTo(2);
        assertThat(sec.getClosedCashSessions()).isEqualTo(1);
        assertThat(sec.getOpenCashSessions()).isEqualTo(1);
        assertThat(sec.getExpectedCashAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(sec.getDeclaredCashAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(sec.getCashDifferenceAmount()).isEqualByComparingTo(new BigDecimal("2.00"));

        assertThat(sec.getSessions()).hasSize(2);
        assertThat(sec.getSessions().get(0).getSessionHash()).isNotBlank();
        assertThat(sec.getWarnings()).contains("OPEN_CASH_SESSION_INCLUDED_IN_TURNO");
        assertThat(sec.getWarnings()).contains("CASH_SESSION_HAS_DIFFERENCE");
    }
}

