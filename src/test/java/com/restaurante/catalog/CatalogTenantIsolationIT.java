package com.restaurante.catalog;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.CategoriaProdutoService;
import com.restaurante.service.ProdutoService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it-postgres")
class CatalogTenantIsolationIT extends PostgresTestcontainersConfig {

    @Autowired TenantRepository tenantRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired SubscricaoRepository subscricaoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired CategoriaProdutoService categoriaProdutoService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProdutoService produtoService;

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
    }

    @Test
    void catalogIsIsolatedByTenant_andCodigoIsUniquePerTenant() {
        Tenant tenantA = criarTenant("Banca da Tia Rosa", "banca-tia-rosa", "TIA-ROSA");
        Tenant tenantB = criarTenant("Bar do João", "bar-do-joao", "BAR-JOAO");

        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();
        criarSubscricaoAtiva(tenantA, piloto);
        criarSubscricaoAtiva(tenantB, piloto);

        User userA = criarUser("tia.rosa", "tia.rosa@teste.local", "+244900000001");
        User userB = criarUser("joao.bar", "joao.bar@teste.local", "+244900000002");
        criarTenantUser(tenantA, userA, TenantUserRole.TENANT_ADMIN);
        criarTenantUser(tenantB, userB, TenantUserRole.TENANT_ADMIN);

        // Categorias com mesmo slug em tenants diferentes (permitido)
        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        CategoriaProduto catB = criarCategoria(tenantB, "Bebidas", "bebidas");
        assertThat(catA.getId()).isNotNull();
        assertThat(catB.getId()).isNotNull();

        // Mesmo slug no mesmo tenant (bloqueado)
        CategoriaProduto catA2 = new CategoriaProduto();
        catA2.setTenant(tenantA);
        catA2.setNome("Bebidas 2");
        catA2.setSlug("bebidas");
        catA2.setOrdem(0);
        catA2.setAtivo(true);
        assertThatThrownBy(() -> categoriaProdutoRepository.saveAndFlush(catA2))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Produtos com mesmo código em tenants diferentes (permitido)
        Produto prodA = criarProduto(tenantA, catA, "AGUA-500", "Água 500ml", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        Produto prodB = criarProduto(tenantB, catB, "AGUA-500", "Água 500ml", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        assertThat(prodA.getId()).isNotNull();
        assertThat(prodB.getId()).isNotNull();

        // Mesmo código no mesmo tenant (bloqueado)
        Produto prodA2 = new Produto();
        prodA2.setTenant(tenantA);
        prodA2.setCodigo("AGUA-500");
        prodA2.setNome("Água duplicada");
        prodA2.setPreco(new BigDecimal("100.00"));
        prodA2.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        prodA2.setCategoriaProduto(catA);
        prodA2.setDisponivel(true);
        prodA2.setAtivo(true);
        assertThatThrownBy(() -> produtoRepository.saveAndFlush(prodA2))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Listagem por tenant não vaza dados
        assertThat(produtoRepository.findByTenantId(tenantA.getId()))
                .extracting(Produto::getId)
                .contains(prodA.getId())
                .doesNotContain(prodB.getId());

        // findByIdAndTenantId não retorna cross-tenant
        assertThat(produtoRepository.findByIdAndTenantId(prodB.getId(), tenantA.getId())).isEmpty();

        // Service tenant-aware bloqueia leitura cross-tenant via scoping (404)
        TenantContextHolder.set(new TenantContext(
                tenantA.getId(),
                tenantA.getTenantCode(),
                userA.getId(),
                Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT,
                false,
                false
        ));
        assertThatThrownBy(() -> produtoService.buscarPorIdDoTenant(prodB.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Tenant A não pode criar produto usando categoriaProdutoId do Tenant B
        com.restaurante.dto.request.ProdutoRequest req = com.restaurante.dto.request.ProdutoRequest.builder()
                .codigo("X-TENANT-001")
                .nome("Cross Tenant")
                .descricao(null)
                .preco(new BigDecimal("10.00"))
                .categoriaProdutoId(catB.getId())
                .disponivel(true)
                .build();
        assertThatThrownBy(() -> produtoService.criarTenantAware(req))
                .isInstanceOf(com.restaurante.exception.BusinessException.class);
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private void criarSubscricaoAtiva(Tenant tenant, Plano plano) {
        Subscricao s = new Subscricao();
        s.setTenant(tenant);
        s.setPlano(plano);
        s.setEstado(SubscricaoEstado.ATIVA);
        s.setInicioEm(LocalDate.now());
        s.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(s);
    }

    private User criarUser(String username, String email, String telefone) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setEmail(email);
        u.setTelefone(telefone);
        u.setRoles(Set.of(Role.ROLE_ADMIN));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private void criarTenantUser(Tenant tenant, User user, TenantUserRole role) {
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(role);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);
    }

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto cp = new CategoriaProduto();
        cp.setTenant(tenant);
        cp.setNome(nome);
        cp.setSlug(slug);
        cp.setOrdem(0);
        cp.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(cp);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoriaProduto, String codigo, String nome, CategoriaProdutoLegacy categoria) {
        if (categoriaProduto == null) {
            categoriaProduto = categoriaProdutoService.getOrCreateDefaultDoTenant(tenant.getId());
        }
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setDescricao(null);
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(categoria);
        p.setCategoriaProduto(categoriaProduto);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
