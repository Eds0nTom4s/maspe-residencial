package com.restaurante.inventory;

import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.inventory.service.*;
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
        "consuma.inventory.enabled=true"
})
public class InventoryConsumptionServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ItemPedidoRepository itemPedidoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired private CozinhaRepository cozinhaRepository;

    @Autowired private InventoryUnitService unitService;
    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryStockService stockService;
    @Autowired private InventoryRecipeService recipeService;
    @Autowired private ProductInventoryMappingService mappingService;
    @Autowired private InventoryConsumptionService consumptionService;

    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private InventoryConsumptionRecordRepository recordRepository;

    @Test
    @Transactional
    void pedidoPagoGeraConsumoIdempotenteECusto() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        TurnoOperacional turno = criarTurno(tenant, inst, ua);
        SessaoConsumo sessao = criarSessao(tenant, inst, ua);

        Produto produto = criarProduto(tenant);
        UnitOfMeasure unit = unitService.createUnit(tenant, "UNIT", "Unit", UnitOfMeasureType.COUNT, false);

        var ingrediente = itemService.create(tenant, "Carne", "CARNE", InventoryItemType.RAW_MATERIAL, null, unit.getCode(), true, true, null, null);
        stockService.stockIn(tenant.getId(), ingrediente.getId(), new BigDecimal("10"), unit.getCode(), new BigDecimal("100.00"), "Compra", null);

        InventoryRecipe recipe = recipeService.createRecipe(tenant, produto.getId(), "Receita", BigDecimal.ONE, unit.getCode());
        recipeService.addLine(tenant, recipe.getId(), ingrediente.getId(), new BigDecimal("1"), unit.getCode(), BigDecimal.ZERO);
        recipeService.activate(tenant.getId(), recipe.getId());
        mappingService.upsert(tenant, produto.getId(), null, recipe.getId(), ProductStockPolicy.RECIPE_DEDUCTION);

        Pedido pedido = criarPedido(tenant, sessao, turno, new BigDecimal("10.00"));
        ItemPedido itemPedido = criarItemPedido(tenant, pedido, produto, 2, ua);
        pedido.getItens().add(itemPedido);
        pedidoRepository.saveAndFlush(pedido);

        Pagamento pg = criarPagamentoConfirmado(tenant, pedido);

        var r1 = consumptionService.consumeOnPaymentConfirmed(tenant.getId(), pedido.getId(), pg.getId(), InventoryMovementSource.SYSTEM);
        var r2 = consumptionService.consumeOnPaymentConfirmed(tenant.getId(), pedido.getId(), pg.getId(), InventoryMovementSource.SYSTEM);

        assertThat(r1.getId()).isEqualTo(r2.getId());
        assertThat(recordRepository.findByTenantIdAndPedidoId(tenant.getId(), pedido.getId())).isPresent();

        InventoryItem reloaded = inventoryItemRepository.findById(ingrediente.getId()).orElseThrow();
        assertThat(reloaded.getCurrentQuantity()).isEqualByComparingTo("8.000000");

        assertThat(movementRepository.existsByTenantIdAndReferenceTypeAndReferenceIdAndMovementType(
                tenant.getId(), InventoryMovementReferenceType.PEDIDO, pedido.getId(), InventoryMovementType.SALE_CONSUMPTION)).isTrue();
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Consumo");
        t.setSlug("tenant-consumo");
        t.setTenantCode("INVC");
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

    private User criarUser() {
        User u = new User();
        u.setUsername("user-inv-" + System.nanoTime());
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
        p.setCodigo("PRD-1");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }

    private Pedido criarPedido(Tenant tenant, SessaoConsumo sessao, TurnoOperacional turno, BigDecimal total) {
        Pedido p = Pedido.builder()
                .numero("PED-INV-1")
                .sessaoConsumo(sessao)
                .status(StatusPedido.EM_ANDAMENTO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .total(total)
                .build();
        p.setTenant(tenant);
        p.setTurnoOperacional(turno);
        return pedidoRepository.saveAndFlush(p);
    }

    private ItemPedido criarItemPedido(Tenant tenant, Pedido pedido, Produto produto, int qtd, UnidadeAtendimento ua) {
        SubPedido sp = resolveSubPedido(tenant, pedido, ua);
        ItemPedido it = new ItemPedido();
        it.setPedido(pedido);
        it.setSubPedido(sp);
        it.setProduto(produto);
        it.setQuantidade(qtd);
        it.setPrecoUnitario(produto.getPreco());
        return itemPedidoRepository.saveAndFlush(it);
    }

    @Autowired private SubPedidoRepository subPedidoRepository;

    private SubPedido resolveSubPedido(Tenant tenant, Pedido pedido, UnidadeAtendimento ua) {
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
        return subPedidoRepository.saveAndFlush(sp);
    }

    private Pagamento criarPagamentoConfirmado(Tenant tenant, Pedido pedido) {
        Pagamento p = Pagamento.builder()
                .tenant(tenant)
                .pedido(pedido)
                .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                .amount(pedido.getTotal())
                .status(com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                .observacoes("TEST")
                .build();
        p.confirmar();
        return pagamentoGatewayRepository.saveAndFlush(p);
    }
}
