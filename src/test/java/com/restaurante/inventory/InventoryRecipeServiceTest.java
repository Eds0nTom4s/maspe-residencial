package com.restaurante.inventory;

import com.restaurante.inventory.service.InventoryItemService;
import com.restaurante.inventory.service.InventoryRecipeService;
import com.restaurante.inventory.service.InventoryUnitService;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"consuma.inventory.enabled=true"})
public class InventoryRecipeServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private InventoryUnitService unitService;
    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryRecipeService recipeService;

    @Test
    @Transactional
    void criaAtivaEListaLinhas() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        criarSessao(tenant, inst, ua);
        Produto produto = criarProduto(tenant);

        UnitOfMeasure unit = unitService.createUnit(tenant, "UNIT", "Unit", UnitOfMeasureType.COUNT, false);
        var ingrediente = itemService.create(tenant, "Pão", "PAO", InventoryItemType.RAW_MATERIAL, null, unit.getCode(), true, true, null, null);

        InventoryRecipe recipe = recipeService.createRecipe(tenant, produto.getId(), "Receita", BigDecimal.ONE, unit.getCode());
        recipeService.addLine(tenant, recipe.getId(), ingrediente.getId(), new BigDecimal("2"), unit.getCode(), BigDecimal.ZERO);

        var lines = recipeService.listLines(tenant.getId(), recipe.getId());
        assertThat(lines).hasSize(1);

        recipeService.activate(tenant.getId(), recipe.getId());
        InventoryRecipe activated = recipeService.activate(tenant.getId(), recipe.getId());
        assertThat(activated.getStatus()).isEqualTo(InventoryRecipeStatus.ACTIVE);
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Recipe");
        t.setSlug("tenant-recipe");
        t.setTenantCode("INVR");
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
        CategoriaProduto cat = resolveCategoriaProduto(tenant);
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("P1");
        p.setNome("Produto");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }

    @Autowired private CategoriaProdutoRepository categoriaProdutoRepository;

    private CategoriaProduto resolveCategoriaProduto(Tenant tenant) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome("Categoria");
        c.setSlug("categoria-" + System.nanoTime());
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }
}
