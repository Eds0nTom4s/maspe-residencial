package com.restaurante.fiscal.autoissue;

import com.restaurante.fiscal.autoissue.service.FiscalAutoIssueJobService;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.tax.enabled=true",
        "consuma.tax.document.auto-issue-on-payment=true",
        "consuma.tax.document.auto-issue.enabled=true"
})
public class FiscalAutoIssueJobServiceTest {

    @Autowired private FiscalAutoIssueJobService jobService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private CozinhaRepository cozinhaRepository;
    @Autowired private UnidadeProducaoRepository unidadeProducaoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;

    @Test
    @Transactional
    void criaJobQuandoElegivelEIdempotente() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        Cozinha cozinha = criarCozinha();
        UnidadeProducao up = criarUnidadeProducao(tenant, inst, ua);
        TurnoOperacional turno = criarTurno(tenant, inst, ua);
        SessaoConsumo sessao = criarSessao(tenant, inst, ua);
        Pedido pedido = criarPedido(tenant, sessao, turno);
        Pagamento pg = criarPagamentoConfirmado(tenant, pedido);

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setFiscalDocumentEnabled(true);
        fiscalProfileRepository.saveAndFlush(profile);

        var j1 = jobService.createAutoOnPaymentConfirmedJobIfEligible(tenant, ua, pedido, pg, sessao, null, FiscalAutoIssueSource.CASH_MANUAL_PAYMENT);
        var j2 = jobService.createAutoOnPaymentConfirmedJobIfEligible(tenant, ua, pedido, pg, sessao, null, FiscalAutoIssueSource.CASH_MANUAL_PAYMENT);

        assertThat(j1).isNotNull();
        assertThat(j2).isNotNull();
        assertThat(j2.getId()).isEqualTo(j1.getId());
        assertThat(j1.getStatus()).isEqualTo(FiscalAutoIssueJobStatus.PENDING);
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Fiscal");
        t.setSlug("tenant-fiscal");
        t.setTenantCode("FISC");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("I");
        i.setNif("5000000000");
        i.setTelefoneAutorizacao("+244900000000");
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

    private Cozinha criarCozinha() {
        Cozinha c = new Cozinha();
        c.setNome("Cozinha");
        c.setTipo(TipoCozinha.CENTRAL);
        c.setAtiva(true);
        return cozinhaRepository.saveAndFlush(c);
    }

    private UnidadeProducao criarUnidadeProducao(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        UnidadeProducao up = new UnidadeProducao();
        up.setTenant(tenant);
        up.setInstituicao(inst);
        up.setUnidadeAtendimento(ua);
        up.setNome("UP");
        up.setCodigo("UP-1");
        up.setAtivo(true);
        up.setTipo(UnidadeProducaoTipo.OUTRO);
        return unidadeProducaoRepository.saveAndFlush(up);
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

    private User criarUser() {
        User u = new User();
        u.setUsername("user-fiscal-" + System.nanoTime());
        u.setPassword("x");
        u.setTelefone("+244900" + (int) (Math.random() * 1000000));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
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

    private Pedido criarPedido(Tenant tenant, SessaoConsumo sessao, TurnoOperacional turno) {
        Pedido p = Pedido.builder()
                .numero("PED-001")
                .sessaoConsumo(sessao)
                .status(StatusPedido.EM_ANDAMENTO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .total(new BigDecimal("10.00"))
                .build();
        p.setTenant(tenant);
        p.setTurnoOperacional(turno);
        return pedidoRepository.saveAndFlush(p);
    }

    private Pagamento criarPagamentoConfirmado(Tenant tenant, Pedido pedido) {
        Pagamento p = Pagamento.builder()
                .tenant(tenant)
                .pedido(pedido)
                .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                .amount(new BigDecimal("10.00"))
                .status(com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                .observacoes("TEST")
                .build();
        p.confirmar();
        return pagamentoGatewayRepository.saveAndFlush(p);
    }
}
