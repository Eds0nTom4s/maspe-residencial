package com.restaurante.inventory;

import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.service.InventoryItemService;
import com.restaurante.inventory.service.InventoryRecipeService;
import com.restaurante.inventory.service.InventoryReturnService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.inventory.enabled=true"
})
public class InventoryReturnFlowIT {

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
    @Autowired private InventoryReturnService returnService;
    @Autowired private InventoryItemRepository itemRepository;
    @Autowired private com.restaurante.inventory.listener.InventoryConsumptionOnPaymentConfirmedListener listener;

    @Test
    void devolucaoParcialRestockReverteStockESegundaDevolucaoExcedeConsumoFalha() {
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
                    .numero("PED-RET")
                    .sessaoConsumo(sessao)
                    .status(StatusPedido.EM_ANDAMENTO)
                    .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                    .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                    .total(new BigDecimal("20.00"))
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
            itemPedido.setQuantidade(2);
            itemPedido.setPrecoUnitario(produto.getPreco());
            itemPedido = itemPedidoRepository.saveAndFlush(itemPedido);
            pedido.getItens().add(itemPedido);
            pedidoRepository.saveAndFlush(pedido);

            Pagamento pg = Pagamento.builder()
                    .tenant(tenant)
                    .pedido(pedido)
                    .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                    .amount(new BigDecimal("20.00"))
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

            return new long[]{tenant.getId(), pedido.getId(), pg.getId(), itemPedido.getId(), ingrediente.getId(), ua.getId(), sessao.getId()};
        });

        Long tenantId = ids[0];
        Long pedidoId = ids[1];
        Long pagamentoId = ids[2];
        Long pedidoItemId = ids[3];
        Long ingredienteId = ids[4];
        Long uaId = ids[5];
        Long sessaoId = ids[6];

        listener.onPaymentConfirmed(new PaymentConfirmedForFiscalIssueEvent(
                tenantId, uaId, pedidoId, pagamentoId, sessaoId, null, FiscalAutoIssueSource.ADMIN_MANUAL_TRIGGER
        ));

        InventoryItem afterConsume = itemRepository.findById(ingredienteId).orElseThrow();
        assertThat(afterConsume.getCurrentQuantity()).isEqualByComparingTo(new BigDecimal("3.000000"));

        InventoryReturnRecord created = returnService.createReturn(
                tenantId,
                pedidoId,
                InventoryReturnType.PARTIAL_ORDER_RETURN,
                InventoryReturnReasonCategory.CUSTOMER_RETURN,
                "teste",
                List.of(new InventoryReturnService.RequestedReturnLine(pedidoItemId, BigDecimal.ONE, InventoryRestockPolicy.RESTOCK)),
                InventoryReturnSource.ADMIN,
                null
        );
        assertThat(created.getStatus()).isEqualTo(InventoryReturnStatus.SUBMITTED);

        InventoryReturnRecord approved = returnService.approve(tenantId, created.getId(), null);
        InventoryReturnRecord processed = returnService.processReturn(tenantId, approved.getId(), null);
        assertThat(processed.getStatus()).isEqualTo(InventoryReturnStatus.PROCESSED);

        InventoryItem afterReturn = itemRepository.findById(ingredienteId).orElseThrow();
        assertThat(afterReturn.getCurrentQuantity()).isEqualByComparingTo(new BigDecimal("4.000000"));

        assertThatThrownBy(() -> returnService.createReturn(
                tenantId,
                pedidoId,
                InventoryReturnType.PARTIAL_ORDER_RETURN,
                InventoryReturnReasonCategory.CUSTOMER_RETURN,
                "exceed",
                List.of(new InventoryReturnService.RequestedReturnLine(pedidoItemId, new BigDecimal("2"), InventoryRestockPolicy.RESTOCK)),
                InventoryReturnSource.ADMIN,
                null
        )).isInstanceOf(com.restaurante.exception.BusinessException.class);
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Return");
        t.setSlug("tenant-return");
        t.setTenantCode("INVR");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("IR" + (System.nanoTime() % 1_000_000));
        i.setNif("5000000004");
        i.setTelefoneAutorizacao("+244900000004");
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
        u.setUsername("user-ret-" + System.nanoTime());
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
        cat.setSlug("cat-ret-" + System.nanoTime());
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.saveAndFlush(cat);

        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("PRD-RET");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
