package com.restaurante.fiscal.autoissue;

import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.autoissue.worker.FiscalAutoIssueWorker;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.tax.enabled=true",
        "consuma.tax.document.auto-issue.enabled=true",
        "consuma.tax.document.auto-issue-on-payment=false",
        "consuma.tax.document.auto-issue.batch-size=10",
        "consuma.tax.document.auto-issue.max-attempts=3",
        "consuma.tax.document.auto-issue.worker-id=test-worker"
})
public class FiscalAutoIssueWorkerTest {

    @Autowired private FiscalAutoIssueWorker worker;
    @Autowired private FiscalAutoIssueJobRepository jobRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private CozinhaRepository cozinhaRepository;
    @Autowired private UnidadeProducaoRepository unidadeProducaoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ItemPedidoRepository itemPedidoRepository;
    @Autowired private SubPedidoRepository subPedidoRepository;
    @Autowired private CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired private TenantFiscalProfileRepository fiscalProfileRepository;
    @Autowired private TaxRateRepository taxRateRepository;
    @Autowired private TenantTaxPolicyRepository taxPolicyRepository;

    @Test
    void processaJobEPersisteDocumentoEmitido() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        Cozinha cozinha = criarCozinha();
        UnidadeProducao up = criarUnidadeProducao(tenant, inst, ua);
        TurnoOperacional turno = criarTurno(tenant, inst, ua);
        SessaoConsumo sessao = criarSessao(tenant, inst, ua);

        CategoriaProduto cat = criarCategoria(tenant);
        Produto prod = criarProduto(tenant, cat);

        Pedido pedido = criarPedidoComItem(tenant, sessao, turno, ua, cozinha, up, prod);
        Pagamento pg = criarPagamentoConfirmado(tenant, pedido);

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setFiscalDocumentEnabled(true);

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

        profile.setDefaultTaxPolicy(policy);
        fiscalProfileRepository.saveAndFlush(profile);

        FiscalAutoIssueJob job = new FiscalAutoIssueJob();
        job.setTenant(tenant);
        job.setUnidadeAtendimento(ua);
        job.setPedido(pedido);
        job.setPagamento(pg);
        job.setSessaoConsumo(sessao);
        job.setSource(FiscalAutoIssueSource.CASH_MANUAL_PAYMENT);
        job.setTriggerType(FiscalAutoIssueTriggerType.ADMIN_FORCE_ISSUE);
        job.setStatus(FiscalAutoIssueJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setIdempotencyKey("tenant:" + tenant.getId() + ":pedido:" + pedido.getId() + ":pagamento:" + pg.getId() + ":fiscal-auto-issue:v1");
        job.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        job = jobRepository.saveAndFlush(job);

        worker.processOneClaiming(job.getId());

        FiscalAutoIssueJob updated = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus())
                .withFailMessage("status=%s errorCode=%s errorMessage=%s", updated.getStatus(), updated.getErrorCode(), updated.getErrorMessage())
                .isEqualTo(FiscalAutoIssueJobStatus.ISSUED);
        assertThat(updated.getFiscalDocument()).isNotNull();
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Fiscal Worker");
        t.setSlug("tenant-fiscal-worker");
        t.setTenantCode("FISCW");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("I");
        i.setNif("5000000001");
        i.setTelefoneAutorizacao("+244900000001");
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
        u.setUsername("user-worker-" + System.nanoTime());
        u.setPassword("x");
        u.setTelefone("+244902" + (int) (Math.random() * 1000000));
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

    private CategoriaProduto criarCategoria(Tenant tenant) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome("Cat");
        c.setSlug("cat");
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto cat) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("P1");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido criarPedidoComItem(Tenant tenant, SessaoConsumo sessao, TurnoOperacional turno, UnidadeAtendimento ua, Cozinha cozinha, UnidadeProducao up, Produto prod) {
        Pedido p = Pedido.builder()
                .numero("PED-002")
                .sessaoConsumo(sessao)
                .status(StatusPedido.EM_ANDAMENTO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .total(new BigDecimal("10.00"))
                .build();
        p.setTenant(tenant);
        p.setTurnoOperacional(turno);
        p = pedidoRepository.saveAndFlush(p);

        SubPedido sp = new SubPedido();
        sp.setTenant(tenant);
        sp.setPedido(p);
        sp.setUnidadeAtendimento(ua);
        sp.setCozinha(cozinha);
        sp.setUnidadeProducao(up);
        sp.setNumero("PED-002-1");
        sp.setStatus(StatusSubPedido.PENDENTE);
        sp.setTotal(new BigDecimal("10.00"));
        sp = subPedidoRepository.saveAndFlush(sp);

        ItemPedido item = new ItemPedido();
        item.setPedido(p);
        item.setSubPedido(sp);
        item.setProduto(prod);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item = itemPedidoRepository.saveAndFlush(item);

        p.getSubPedidos().add(sp);
        p.getItens().add(item);
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
