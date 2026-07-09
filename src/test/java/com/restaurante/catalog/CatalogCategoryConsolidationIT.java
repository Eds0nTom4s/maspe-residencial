package com.restaurante.catalog;

import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.service.CategoriaProdutoService;
import com.restaurante.testsupport.UniqueTestData;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
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
class CatalogCategoryConsolidationIT extends PostgresTestcontainersConfig {

    @Autowired TenantRepository tenantRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired SubscricaoRepository subscricaoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired CategoriaProdutoService categoriaProdutoService;
    @Autowired ProdutoRepository produtoRepository;

    @Test
    void categoriaProdutoFkIsMandatory_andDefaultCategoryExistsPerTenant() {
        String tenantSlug = UniqueTestData.uniqueSlug("tenant-cat-fk");
        String tenantCode = UniqueTestData.uniqueTenantCode("TCFK");
        String username = UniqueTestData.uniqueUsername("u-cat-fk");
        String email = UniqueTestData.uniqueEmail("u-cat-fk");
        String phone = UniqueTestData.uniqueTelefone();
        String validProductCode = UniqueTestData.uniqueTenantCode("FKOK");
        String invalidProductCode = UniqueTestData.uniqueTenantCode("FKNC");

        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Cat FK");
        tenant.setSlug(tenantSlug);
        tenant.setTenantCode(tenantCode);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();
        Subscricao subs = new Subscricao();
        subs.setTenant(tenant);
        subs.setPlano(piloto);
        subs.setEstado(SubscricaoEstado.ATIVA);
        subs.setInicioEm(LocalDate.now());
        subs.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(subs);

        User user = new User();
        user.setUsername(username);
        user.setPassword("x");
        user.setEmail(email);
        user.setTelefone(phone);
        user.setRoles(Set.of(Role.ROLE_ADMIN));
        user.setAtivo(true);
        user = userRepository.saveAndFlush(user);

        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(TenantUserRole.TENANT_ADMIN);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);

        CategoriaProduto geral = categoriaProdutoService.getOrCreateDefaultDoTenant(tenant.getId());
        assertThat(geral.getId()).isNotNull();
        assertThat(geral.getSlug()).isEqualTo("geral");

        Produto ok = new Produto();
        ok.setTenant(tenant);
        ok.setCodigo(validProductCode);
        ok.setNome("Produto FK OK");
        ok.setPreco(new BigDecimal("10.00"));
        ok.setCategoria(CategoriaProdutoLegacy.OUTROS);
        ok.setCategoriaProduto(geral);
        ok.setDisponivel(true);
        ok.setAtivo(true);
        ok = produtoRepository.saveAndFlush(ok);

        Produto loaded = produtoRepository.findByIdAndTenantId(ok.getId(), tenant.getId()).orElseThrow();
        assertThat(loaded.getCategoriaProduto()).isNotNull();
        assertThat(loaded.getCategoriaProduto().getId()).isEqualTo(geral.getId());
        CategoriaProduto loadedCategory = categoriaProdutoRepository
                .findByIdAndTenantId(geral.getId(), tenant.getId())
                .orElseThrow();
        assertThat(loadedCategory.getSlug()).isEqualTo("geral");

        Produto invalid = new Produto();
        invalid.setTenant(tenant);
        invalid.setCodigo(invalidProductCode);
        invalid.setNome("Produto FK inválido");
        invalid.setPreco(new BigDecimal("10.00"));
        invalid.setCategoria(CategoriaProdutoLegacy.OUTROS);
        invalid.setDisponivel(true);
        invalid.setAtivo(true);

        assertThatThrownBy(() -> produtoRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
