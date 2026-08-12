package com.restaurante.android.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurante.android.options.dto.AndroidProductOptionGroupProjection;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.ProductOption;
import com.restaurante.model.entity.ProductOptionGroup;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.VariacaoProduto;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProductOptionGroupRepository;
import com.restaurante.repository.ProductOptionRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.VariacaoProdutoRepository;
import com.restaurante.service.CanonicalProductOptionsService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("it-postgres")
@Transactional
class CanonicalProductOptionsPostgresIT extends PostgresTestcontainersConfig {
    @Autowired TenantRepository tenants;
    @Autowired CategoriaProdutoRepository categories;
    @Autowired ProdutoRepository products;
    @Autowired VariacaoProdutoRepository legacy;
    @Autowired ProductOptionGroupRepository groups;
    @Autowired ProductOptionRepository options;
    @Autowired CanonicalProductOptionsService writes;
    @Autowired CanonicalProductOptionsCompatibilityService compatibility;
    @Autowired ProductOptionSelectionValidator selections;
    @Autowired JdbcTemplate jdbc;

    @Test
    void projectsCanonicalOptionsAndValidatesSelectionsWithoutAnyPublicEndpoint() {
        String suffix = suffix();
        Produto product = product(tenant("A", suffix), "A", suffix);
        ProductOptionGroup size = writes.createGroup(product.getTenant().getId(), product.getId(),
                new CanonicalProductOptionsService.GroupCommand("Tamanho", 1, 1, 0, true));
        ProductOption small = writes.createOption(product.getTenant().getId(), size.getId(),
                new CanonicalProductOptionsService.OptionCommand("Pequeno", new BigDecimal("0.00"), true, false, 0, true));
        ProductOption large = writes.createOption(product.getTenant().getId(), size.getId(),
                new CanonicalProductOptionsService.OptionCommand("Grande", new BigDecimal("500.00"), true, true, 1, true));

        assertThat(size.getPublicId().version()).isEqualTo(4);
        assertThat(large.getPublicId().version()).isEqualTo(4);
        assertThat(compatibility.compatibilityOf(product)).isEqualTo(CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS);
        List<AndroidProductOptionGroupProjection> projection = compatibility.projectForAndroid(product);
        assertThat(projection).singleElement().satisfies(group -> {
            assertThat(group.optionGroupId()).isEqualTo(size.getPublicId());
            assertThat(group.required()).isTrue();
            assertThat(group.singleChoice()).isTrue();
            assertThat(group.options()).extracting(option -> option.optionId()).containsExactly(small.getPublicId(), large.getPublicId());
            assertThat(group.options().get(1).additionalPrice().amountMinor()).isEqualTo(50_000L);
            assertThat(group.options().get(1).additionalPrice().currencyCode()).isEqualTo("AOA");
        });
        assertThat(selections.validate(product, List.of(large.getPublicId())).additionalPrice())
                .isEqualByComparingTo("500.00");
        assertThatThrownBy(() -> selections.validate(product, List.of()))
                .isInstanceOf(ProductOptionSelectionException.class)
                .extracting("reason").isEqualTo(ProductOptionSelectionException.Reason.REQUIRED_GROUP_UNSATISFIED);
        assertThatThrownBy(() -> selections.validate(product, List.of(large.getPublicId(), large.getPublicId())))
                .isInstanceOf(ProductOptionSelectionException.class)
                .extracting("reason").isEqualTo(ProductOptionSelectionException.Reason.DUPLICATE_OPTION);
        assertThatThrownBy(() -> selections.validate(product, List.of(small.getPublicId(), large.getPublicId())))
                .isInstanceOf(ProductOptionSelectionException.class)
                .extracting("reason").isEqualTo(ProductOptionSelectionException.Reason.TOO_MANY_SELECTIONS);
        assertThatThrownBy(() -> selections.validate(product, List.of(UUID.randomUUID())))
                .isInstanceOf(ProductOptionSelectionException.class)
                .extracting("reason").isEqualTo(ProductOptionSelectionException.Reason.UNKNOWN_OPTION);
    }

    @Test
    void distinguishesNoOptionsLegacyAndInvalidCanonicalConfigurations() {
        String suffix = suffix();
        Produto noOptions = product(tenant("N", suffix), "N", suffix);
        Produto legacyProduct = product(tenant("L", suffix), "L", suffix);
        legacy.saveAndFlush(new VariacaoProduto(legacyProduct, VariacaoProduto.TipoVariacao.TAMANHO, "M", null, null, null,
                new BigDecimal("10.00"), null, true));
        Produto invalidProduct = product(tenant("I", suffix), "I", suffix);
        writes.createGroup(invalidProduct.getTenant().getId(), invalidProduct.getId(),
                new CanonicalProductOptionsService.GroupCommand("Extras", 0, 3, 0, true));

        assertThat(compatibility.compatibilityOf(noOptions)).isEqualTo(CanonicalProductOptionsCompatibility.NO_OPTIONS);
        assertThat(compatibility.projectForAndroid(noOptions)).isEmpty();
        assertThat(compatibility.compatibilityOf(legacyProduct)).isEqualTo(CanonicalProductOptionsCompatibility.LEGACY_OPTIONS_UNMIGRATED);
        assertThatThrownBy(() -> compatibility.projectForAndroid(legacyProduct))
                .isInstanceOf(ProductOptionsNotProjectableException.class);
        assertThat(compatibility.compatibilityOf(invalidProduct)).isEqualTo(CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS_INVALID);
        assertThatThrownBy(() -> compatibility.projectForAndroid(invalidProduct))
                .isInstanceOf(ProductOptionsNotProjectableException.class);

        Produto defaultsProduct = product(tenant("D", suffix), "D", suffix);
        ProductOptionGroup defaults = writes.createGroup(defaultsProduct.getTenant().getId(), defaultsProduct.getId(),
                new CanonicalProductOptionsService.GroupCommand("Escolha", 0, 1, 0, true));
        writes.createOption(defaultsProduct.getTenant().getId(), defaults.getId(),
                new CanonicalProductOptionsService.OptionCommand("Um", BigDecimal.ZERO, true, true, 0, true));
        writes.createOption(defaultsProduct.getTenant().getId(), defaults.getId(),
                new CanonicalProductOptionsService.OptionCommand("Dois", BigDecimal.ZERO, true, true, 1, true));
        assertThat(compatibility.compatibilityOf(defaultsProduct))
                .isEqualTo(CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS_INVALID);
        assertThatThrownBy(() -> writes.createGroup(defaultsProduct.getTenant().getId(), defaultsProduct.getId(),
                new CanonicalProductOptionsService.GroupCommand("Inválido", 2, 1, 0, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writes.createOption(defaultsProduct.getTenant().getId(), defaults.getId(),
                new CanonicalProductOptionsService.OptionCommand("Inválida", BigDecimal.ZERO, false, true, 2, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseEnforcesTenantOwnershipAndMigrationPreservesLegacyRows() {
        String suffix = suffix();
        Tenant tenantA = tenant("A", suffix);
        Produto productA = product(tenantA, "A", suffix);
        long legacyBefore = legacy.count();
        legacy.saveAndFlush(new VariacaoProduto(productA, VariacaoProduto.TipoVariacao.COR, "Azul", null, "Azul", null,
                null, null, true));
        assertThat(legacy.count()).isEqualTo(legacyBefore + 1);

        assertThat(jdbc.queryForObject("select count(*) from product_option_groups", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product_options", Long.class)).isNotNegative();
    }

    @Test
    void databaseRejectsGroupFromAnotherTenantProduct() {
        String suffix = suffix();
        Tenant tenantA = tenant("A", suffix);
        Tenant tenantB = tenant("B", suffix);
        Produto productB = product(tenantB, "B", suffix);
        assertThatThrownBy(() -> jdbc.update("insert into product_option_groups "
                        + "(created_at, tenant_id, produto_id, name, min_selections, max_selections, sort_order, active) "
                        + "values (current_timestamp, ?, ?, 'ilegível', 0, 1, 0, true)", tenantA.getId(), productB.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsOptionFromAnotherTenantGroup() {
        String suffix = suffix();
        Tenant tenantA = tenant("A", suffix);
        Tenant tenantB = tenant("B", suffix);
        Produto productA = product(tenantA, "A", suffix);
        ProductOptionGroup groupA = writes.createGroup(tenantA.getId(), productA.getId(),
                new CanonicalProductOptionsService.GroupCommand("Extras", 0, 3, 0, true));
        assertThatThrownBy(() -> jdbc.update("insert into product_options "
                        + "(created_at, tenant_id, option_group_id, name, additional_price, available, default_selected, sort_order, active) "
                        + "values (current_timestamp, ?, ?, 'ilegível', 0, true, false, 0, true)", tenantB.getId(), groupA.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Tenant tenant(String label, String suffix) {
        Tenant tenant = new Tenant();
        tenant.setNome("Options " + label);
        tenant.setSlug("options-" + label.toLowerCase() + "-" + suffix);
        tenant.setTenantCode(("O" + label + suffix).toUpperCase());
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenants.saveAndFlush(tenant);
    }

    private Produto product(Tenant tenant, String label, String suffix) {
        CategoriaProduto category = new CategoriaProduto();
        category.setTenant(tenant);
        category.setNome("Categoria " + label);
        category.setSlug("category-options-" + label.toLowerCase() + "-" + suffix);
        category.setOrdem(0);
        category.setAtivo(true);
        category = categories.saveAndFlush(category);
        Produto product = new Produto();
        product.setTenant(tenant);
        product.setCategoriaProduto(category);
        product.setCategoria(CategoriaProdutoLegacy.OUTROS);
        product.setCodigo("OPTIONS-" + label + "-" + suffix);
        product.setNome("Produto Options " + label);
        product.setPreco(new BigDecimal("100.00"));
        product.setAtivo(true);
        product.setDisponivel(true);
        return products.saveAndFlush(product);
    }

    private String suffix() { return UUID.randomUUID().toString().substring(0, 8); }
}
