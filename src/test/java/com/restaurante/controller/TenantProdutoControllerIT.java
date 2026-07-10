package com.restaurante.controller;

import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.ProdutoImagem;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoImagemRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantProdutoControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired ProdutoImagemRepository produtoImagemRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void listarProdutosTenantSerializaGaleriaSemLazyInitializationException() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        Tenant tenant = criarTenant("cardapio-regressao-" + suffix, "CARD" + suffix);
        User owner = criarUsuario("owner-cardapio-" + suffix + "@consuma.local", "+24493" + String.format("%07d", Long.parseLong(suffix)));
        vincularUsuario(tenant, owner, TenantUserRole.TENANT_OWNER);
        criarProdutoComGaleria(tenant);

        String token = jwtTokenProvider.generateTenantScopedToken(
                owner,
                tenant,
                TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO,
                1,
                null
        );

        mockMvc.perform(get("/tenant/produtos")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].codigo").value("CARDAPIO-GALERIA"))
                .andExpect(jsonPath("$.data.content[0].imagensGaleria[0]").value("https://cdn.consuma.local/produtos/galeria-1.jpg"));
    }

    private Tenant criarTenant(String slug, String tenantCode) {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant " + slug);
        tenant.setSlug(slug);
        tenant.setTenantCode(tenantCode.length() > 20 ? tenantCode.substring(0, 20) : tenantCode);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(tenant);
    }

    private User criarUsuario(String email, String telefone) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setTelefone(telefone);
        user.setPassword("x");
        user.setRoles(Set.of(Role.ROLE_GERENTE));
        user.setAtivo(true);
        return userRepository.saveAndFlush(user);
    }

    private void vincularUsuario(Tenant tenant, User user, TenantUserRole role) {
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(user);
        tenantUser.setRole(role);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tenantUser);
    }

    private void criarProdutoComGaleria(Tenant tenant) {
        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome("Geral");
        categoria.setSlug("geral");
        categoria.setOrdem(1);
        categoria.setAtivo(true);
        CategoriaProduto savedCategoria = categoriaProdutoRepository.saveAndFlush(categoria);

        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo("CARDAPIO-GALERIA");
        produto.setNome("Produto com Galeria");
        produto.setDescricao("Produto de regressao para serializacao da galeria.");
        produto.setPreco(new BigDecimal("1000.00"));
        produto.setCategoria(CategoriaProdutoLegacy.OUTROS);
        produto.setCategoriaProduto(savedCategoria);
        produto.setUrlImagem("https://cdn.consuma.local/produtos/principal.jpg");
        produto.setTempoPreparoMinutos(5);
        produto.setDisponivel(true);
        produto.setAtivo(true);
        Produto savedProduto = produtoRepository.saveAndFlush(produto);

        criarImagem(tenant, savedProduto, 0, "https://cdn.consuma.local/produtos/galeria-1.jpg");
        criarImagem(tenant, savedProduto, 1, "https://cdn.consuma.local/produtos/galeria-2.jpg");
    }

    private void criarImagem(Tenant tenant, Produto produto, int ordem, String url) {
        ProdutoImagem imagem = new ProdutoImagem();
        imagem.setTenant(tenant);
        imagem.setProduto(produto);
        imagem.setUrl(url);
        imagem.setOrdem(ordem);
        produtoImagemRepository.saveAndFlush(imagem);
    }
}
