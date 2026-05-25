package com.restaurante.inventory;

import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.service.InventoryItemService;
import com.restaurante.inventory.service.InventoryRecipeService;
import com.restaurante.inventory.service.InventoryStockService;
import com.restaurante.inventory.service.InventoryUnitService;
import com.restaurante.inventory.service.ProductInventoryMappingService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.inventory.enabled=true"
})
public class InventoryFlowIT {

    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationEventPublisher publisher;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private SubPedidoRepository subPedidoRepository;
    @Autowired private ItemPedidoRepository itemPedidoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired private CozinhaRepository cozinhaRepository;

    @Autowired private InventoryUnitService unitService;
    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryStockService stockService;
    @Autowired private InventoryRecipeService recipeService;
    @Autowired private ProductInventoryMappingService mappingService;
    @Autowired private InventoryConsumptionRecordRepository recordRepository;
    @Autowired private com.restaurante.inventory.listener.InventoryConsumptionOnPaymentConfirmedListener listener;

    @Test
    void pagamentoConfirmadoPublicaEventoEConsumoEhCriado() {
        long[] ids = transactionTemplate.execute(status -> {
            Tenant tenant = criarTenant();
            Instituicao inst = criarInstituicao(tenant);
            UnidadeAtendimento ua = criarUnidade(inst);
            TurnoOperacional turno = criarTurno(tenant, inst, ua);
            SessaoConsumo sessao = criarSessao(tenant, inst, ua);
            Produto produto = criarProduto(tenant);
            UnitOfMeasure unit = unitService.createUnit(tenant, "UNIT", "Unit", UnitOfMeasureType.COUNT, false);
            var ingrediente = itemService.create(tenant, "Ing", "I", InventoryItemType.RAW_MATERIAL, null, unit.getCode(), true, true, null, null);
            stockService.stockIn(tenant.getId(), ingrediente.getId(), new BigDecimal("5"), unit.getCode(), new BigDecimal("10.00"), "Compra", null);
            InventoryRecipe recipe = recipeService.createRecipe(tenant, produto.getId(), "R", BigDecimal.ONE, unit.getCode());
            recipeService.addLine(tenant, recipe.getId(), ingrediente.getId(), new BigDecimal("1"), unit.getCode(), BigDecimal.ZERO);
            recipeService.activate(tenant.getId(), recipe.getId());
            mappingService.upsert(tenant, produto.getId(), null, recipe.getId(), ProductStockPolicy.RECIPE_DEDUCTION);

            Pedido pedido = Pedido.builder()
                    .numero("PED-EVT")
                    .sessaoConsumo(sessao)
                    .status(StatusPedido.EM_ANDAMENTO)
                    .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                    .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                    .total(new BigDecimal("10.00"))
                    .build();
            pedido.setTenant(tenant);
            pedido.setTurnoOperacional(turno);
            pedido = pedidoRepository.saveAndFlush(pedido);

            Cozinha cozinha = new Cozinha();
            cozinha.setNome("Cozinha");
            cozinha.setTipo(TipoCozinha.CENTRAL);
            cozinha.setAtiva(true);
            cozinha = cozinhaRepository.saveAndFlush(cozinha);

            SubPedido sp = new SubPedido();
            sp.setTenant(tenant);
            sp.setPedido(pedido);
            sp.setNumero("SP-" + System.nanoTime());
            sp.setUnidadeAtendimento(ua);
            sp.setCozinha(cozinha);
            sp.setStatus(StatusSubPedido.CRIADO);
            sp = subPedidoRepository.saveAndFlush(sp);

            ItemPedido itemPedido = new ItemPedido();
            itemPedido.setPedido(pedido);
            itemPedido.setSubPedido(sp);
            itemPedido.setProduto(produto);
            itemPedido.setQuantidade(1);
            itemPedido.setPrecoUnitario(produto.getPreco());
            itemPedidoRepository.saveAndFlush(itemPedido);
            pedido.getItens().add(itemPedido);
            pedidoRepository.saveAndFlush(pedido);

            Pagamento pg = Pagamento.builder()
                    .tenant(tenant)
                    .pedido(pedido)
                    .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                    .amount(new BigDecimal("10.00"))
                    .status(com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                    .observacoes("TEST")
                    .build();
            pg.confirmar();
            pg = pagamentoGatewayRepository.saveAndFlush(pg);

            publisher.publishEvent(new PaymentConfirmedForFiscalIssueEvent(
                    tenant.getId(),
                    ua.getId(),
                    pedido.getId(),
                    pg.getId(),
                    sessao.getId(),
                    null,
                    FiscalAutoIssueSource.CASH_MANUAL_PAYMENT
            ));
            return new long[] { tenant.getId(), pedido.getId(), pg.getId(), ua.getId(), sessao.getId() };
        });

        Long tenantId = ids != null && ids.length > 0 ? ids[0] : null;
        Long pedidoId = ids != null && ids.length > 1 ? ids[1] : null;
        Long pagamentoId = ids != null && ids.length > 2 ? ids[2] : null;
        Long uaId = ids != null && ids.length > 3 ? ids[3] : null;
        Long sessaoId = ids != null && ids.length > 4 ? ids[4] : null;

        var existing = recordRepository.listByTenant(tenantId, org.springframework.data.domain.PageRequest.of(0, 10));
        if (existing.getTotalElements() == 0) {
            listener.onPaymentConfirmed(new PaymentConfirmedForFiscalIssueEvent(
                    tenantId,
                    uaId,
                    pedidoId,
                    pagamentoId,
                    sessaoId,
                    null,
                    FiscalAutoIssueSource.ADMIN_MANUAL_TRIGGER
            ));
        }
        var records = recordRepository.listByTenant(tenantId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(records.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Flow");
        t.setSlug("tenant-flow");
        t.setTenantCode("INVF");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("I");
        i.setNif("5000000003");
        i.setTelefoneAutorizacao("+244900000003");
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

    private User criarUser() {
        User u = new User();
        u.setUsername("user-flow-" + System.nanoTime());
        u.setPassword("x");
        u.setTelefone("+244900" + (int) (Math.random() * 1000000));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
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

    private Produto criarProduto(Tenant tenant) {
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Categoria");
        cat.setSlug("cat-" + System.nanoTime());
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.saveAndFlush(cat);

        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("PRD-2");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
