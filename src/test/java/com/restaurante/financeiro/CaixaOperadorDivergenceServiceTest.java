package com.restaurante.financeiro;

import com.restaurante.financeiro.caixa.divergence.service.CaixaOperadorDivergenceService;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import com.restaurante.model.enums.DeviceCapability;
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
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CaixaOperadorDivergenceServiceTest {

    @Autowired CaixaOperadorDivergenceService divergenceService;
    @Autowired CaixaOperadorSessionRepository caixaRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;

    @Test
    @Transactional
    void autoCreateDraftsIfNeeded_creates_divergences_and_marks_caixa_disputed_and_is_idempotent() {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Div");
        tenant.setSlug("tenant-div-" + System.nanoTime());
        tenant.setTenantCode("DIV" + (System.nanoTime() % 10000));
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.save(tenant);

        Instituicao inst = Instituicao.builder()
                .tenant(tenant)
                .nome("Inst Div")
                .sigla("ID" + (System.nanoTime() % 1000))
                .nif("NIF-" + (System.nanoTime() % 1_000_000))
                .telefoneAutorizacao("+244900000000")
                .ativa(true)
                .build();
        inst = instituicaoRepository.save(inst);

        UnidadeAtendimento ua = UnidadeAtendimento.builder()
                .nome("UA Div")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .ativa(true)
                .instituicao(inst)
                .build();
        ua = unidadeAtendimentoRepository.save(ua);

        User operador = User.builder()
                .username("op-div-" + System.nanoTime())
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
        d.setCodigo("POS-DIV-" + (System.nanoTime() % 1_000_000));
        d.setNome("POS Div");
        d = dispositivoOperacionalRepository.save(d);

        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(inst);
        turno.setUnidadeAtendimento(ua);
        turno.setAbertoPor(operador);
        turno.setStatus(TurnoOperacionalStatus.FECHADO);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno Div");
        turno.setAbertoEm(LocalDateTime.now().minusHours(2));
        turno.setFechadoEm(LocalDateTime.now().minusHours(1));
        turno = turnoOperacionalRepository.save(turno);

        CaixaOperadorSession caixa = new CaixaOperadorSession();
        caixa.setTenant(tenant);
        caixa.setInstituicao(inst);
        caixa.setUnidadeAtendimento(ua);
        caixa.setTurnoOperacional(turno);
        caixa.setDispositivoOperacional(d);
        caixa.setOperador(operador);
        caixa.setOpenedBy(operador);
        caixa.setStatus(CaixaOperadorSessionStatus.CLOSED);
        caixa.setOpenedAt(LocalDateTime.now().minusHours(2));
        caixa.setClosedAt(LocalDateTime.now().minusHours(1));
        caixa.setExpectedCashAmount(new BigDecimal("100.00"));
        caixa.setDeclaredCashAmount(new BigDecimal("90.00"));
        caixa.setCashDifferenceAmount(new BigDecimal("-10.00"));
        caixa.setExpectedTpaAmount(new BigDecimal("200.00"));
        caixa.setDeclaredTpaAmount(new BigDecimal("205.00"));
        caixa.setTpaDifferenceAmount(new BigDecimal("5.00"));
        caixa = caixaRepository.save(caixa);

        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                operador.getId(),
                Set.of(Role.ROLE_GERENTE.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));

        var created1 = divergenceService.autoCreateDraftsIfNeeded(caixa, null, null);
        assertThat(created1).hasSize(3);
        assertThat(created1).allMatch(it -> it.getStatus() == CaixaOperadorDivergenceStatus.DRAFT);
        assertThat(created1).anyMatch(it -> it.getType() == CaixaOperadorDivergenceType.CASH_SHORTAGE);
        assertThat(created1).anyMatch(it -> it.getType() == CaixaOperadorDivergenceType.TPA_SURPLUS);
        assertThat(created1).anyMatch(it -> it.getType() == CaixaOperadorDivergenceType.MIXED_DIFFERENCE);

        CaixaOperadorSession reloaded = caixaRepository.findById(caixa.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CaixaOperadorSessionStatus.DISPUTED);

        var created2 = divergenceService.autoCreateDraftsIfNeeded(reloaded, null, null);
        assertThat(created2).hasSize(3);
        assertThat(created2.stream().map(it -> it.getId()).distinct().count()).isEqualTo(3);

        TenantContextHolder.clear();
    }

    @Test
    @Transactional
    void justify_and_submit_by_device_updates_status_and_fields() {
        // Arrange minimal scenario using the auto-create flow
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Div2");
        tenant.setSlug("tenant-div2-" + System.nanoTime());
        tenant.setTenantCode("DV2" + (System.nanoTime() % 10000));
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.save(tenant);

        Instituicao inst = Instituicao.builder()
                .tenant(tenant)
                .nome("Inst Div2")
                .sigla("I2" + (System.nanoTime() % 1000))
                .nif("NIF-" + (System.nanoTime() % 1_000_000))
                .telefoneAutorizacao("+244900000001")
                .ativa(true)
                .build();
        inst = instituicaoRepository.save(inst);

        UnidadeAtendimento ua = UnidadeAtendimento.builder()
                .nome("UA Div2")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .ativa(true)
                .instituicao(inst)
                .build();
        ua = unidadeAtendimentoRepository.save(ua);

        User operador = User.builder()
                .username("op-div2-" + System.nanoTime())
                .password("x")
                .telefone("+244912" + (System.nanoTime() % 1_000_000))
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
        d.setCodigo("POS-DIV2-" + (System.nanoTime() % 1_000_000));
        d.setNome("POS Div2");
        d = dispositivoOperacionalRepository.save(d);

        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(inst);
        turno.setUnidadeAtendimento(ua);
        turno.setAbertoPor(operador);
        turno.setStatus(TurnoOperacionalStatus.FECHADO);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno Div2");
        turno.setAbertoEm(LocalDateTime.now().minusHours(2));
        turno.setFechadoEm(LocalDateTime.now().minusHours(1));
        turno = turnoOperacionalRepository.save(turno);

        CaixaOperadorSession caixa = new CaixaOperadorSession();
        caixa.setTenant(tenant);
        caixa.setInstituicao(inst);
        caixa.setUnidadeAtendimento(ua);
        caixa.setTurnoOperacional(turno);
        caixa.setDispositivoOperacional(d);
        caixa.setOperador(operador);
        caixa.setOpenedBy(operador);
        caixa.setStatus(CaixaOperadorSessionStatus.CLOSED);
        caixa.setOpenedAt(LocalDateTime.now().minusHours(2));
        caixa.setClosedAt(LocalDateTime.now().minusHours(1));
        caixa.setExpectedCashAmount(new BigDecimal("10.00"));
        caixa.setDeclaredCashAmount(new BigDecimal("9.00"));
        caixa.setCashDifferenceAmount(new BigDecimal("-1.00"));
        caixa = caixaRepository.save(caixa);

        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                operador.getId(),
                Set.of(Role.ROLE_GERENTE.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));

        var created = divergenceService.autoCreateDraftsIfNeeded(caixa, null, null);
        Long divergenceId = created.stream()
                .filter(it -> it.getType() == CaixaOperadorDivergenceType.CASH_SHORTAGE)
                .findFirst().orElseThrow().getId();

        DevicePrincipal device = new DevicePrincipal(
                d.getId(),
                d.getCodigo(),
                tenant.getId(),
                tenant.getTenantCode(),
                inst.getId(),
                ua.getId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.JUSTIFY_OPERATOR_CASH_DIVERGENCE, DeviceCapability.SUBMIT_OPERATOR_CASH_DIVERGENCE),
                1
        );

        var justified = divergenceService.justifyByDevice(device, divergenceId,
                com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory.OPERATOR_COUNTING_ERROR,
                "Diferença apurada no contado físico",
                null, null);
        assertThat(justified.getReasonCategory()).isNotNull();
        assertThat(justified.getDescription()).isNotBlank();
        assertThat(justified.getStatus()).isEqualTo(CaixaOperadorDivergenceStatus.DRAFT);

        var submitted = divergenceService.submitByDevice(device, divergenceId, null, null);
        assertThat(submitted.getStatus()).isEqualTo(CaixaOperadorDivergenceStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isNotNull();
        assertThat(submitted.getSubmittedBy()).isNotNull();

        TenantContextHolder.clear();
    }
}
