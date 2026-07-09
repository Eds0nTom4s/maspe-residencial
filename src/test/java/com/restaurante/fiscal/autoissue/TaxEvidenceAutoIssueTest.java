package com.restaurante.fiscal.autoissue;

import com.restaurante.fiscal.evidence.service.TaxEvidenceService;
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
        "consuma.tax.evidence.enabled=true",
        "consuma.tax.document.auto-issue.enabled=true",
        "consuma.tax.document.auto-issue-on-payment=true"
})
public class TaxEvidenceAutoIssueTest {

    @Autowired private TaxEvidenceService taxEvidenceService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;

    @Test
    @Transactional
    void evidenciaApontaPagamentoConfirmadoSemDocumento() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        TurnoOperacional turno = criarTurno(tenant, inst, ua);
        SessaoConsumo sessao = criarSessao(tenant, inst, ua);
        Pedido pedido = criarPedido(tenant, sessao, turno);
        criarPagamentoConfirmado(tenant, pedido);

        var ev = taxEvidenceService.buildForTurno(tenant.getId(), turno.getId());
        assertThat(ev.getConfirmedPaymentsWithoutFiscalDocument()).isNotNull();
        assertThat(ev.getConfirmedPaymentsWithoutFiscalDocument()).isGreaterThanOrEqualTo(1);
        assertThat(ev.getWarnings()).contains("CONFIRMED_PAYMENT_WITHOUT_FISCAL_DOCUMENT");
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Evidence");
        t.setSlug("tenant-evidence");
        t.setTenantCode("EVID");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("I");
        i.setNif("5000000002");
        i.setTelefoneAutorizacao("+244900000002");
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
        u.setUsername("user-evidence-" + System.nanoTime());
        u.setPassword("x");
        u.setTelefone("+244901" + (int) (Math.random() * 1000000));
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
                .numero("PED-003")
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

    private void criarPagamentoConfirmado(Tenant tenant, Pedido pedido) {
        Pagamento p = Pagamento.builder()
                .tenant(tenant)
                .pedido(pedido)
                .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                .amount(new BigDecimal("10.00"))
                .status(com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                .observacoes("TEST")
                .build();
        p.confirmar();
        pagamentoGatewayRepository.saveAndFlush(p);
    }
}
