package com.restaurante.android.foundation.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("it-postgres")
@Transactional
class AndroidPublicIdentityRepositoryPostgresIT extends PostgresTestcontainersConfig {

    @Autowired TenantRepository tenants;
    @Autowired CategoriaProdutoRepository categories;
    @Autowired ProdutoRepository products;
    @Autowired PedidoRepository orders;
    @Autowired AndroidPublicIdentityLookupService lookup;

    @Test
    void generatesGlobalPublicIdsAndPreventsCrossTenantLookup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantA = tenants.saveAndFlush(tenant("A", suffix));
        Tenant tenantB = tenants.saveAndFlush(tenant("B", suffix));
        CategoriaProduto categoryA = categories.saveAndFlush(category(tenantA, "A", suffix));
        CategoriaProduto categoryB = categories.saveAndFlush(category(tenantB, "B", suffix));
        Produto productA = products.saveAndFlush(product(tenantA, categoryA, "A", suffix));
        Produto productB = products.saveAndFlush(product(tenantB, categoryB, "B", suffix));
        Pedido orderA = orders.saveAndFlush(order(tenantA, "A", suffix));
        Pedido orderB = orders.saveAndFlush(order(tenantB, "B", suffix));

        assertThat(Arrays.asList(
                tenantA.getMerchantPublicId(), tenantB.getMerchantPublicId(),
                categoryA.getPublicId(), categoryB.getPublicId(),
                productA.getPublicId(), productB.getPublicId(),
                orderA.getPublicId(), orderB.getPublicId()))
                .doesNotContainNull()
                .doesNotHaveDuplicates()
                .allMatch(id -> id.version() == 4 && id.variant() == 2);

        assertThat(lookup.resolveMerchant(tenantA.getMerchantPublicId()))
                .get().extracting(MerchantIdentityContext::tenantId).isEqualTo(tenantA.getId());
        assertThat(lookup.resolveProduct(tenantA.getMerchantPublicId(), productA.getPublicId())).contains(productA);
        assertThat(lookup.resolveCategory(tenantA.getMerchantPublicId(), categoryA.getPublicId())).contains(categoryA);
        assertThat(lookup.resolveOrder(tenantA.getMerchantPublicId(), orderA.getPublicId())).contains(orderA);

        assertThat(lookup.resolveProduct(tenantA.getMerchantPublicId(), productB.getPublicId())).isEmpty();
        assertThat(lookup.resolveCategory(tenantA.getMerchantPublicId(), categoryB.getPublicId())).isEmpty();
        assertThat(lookup.resolveOrder(tenantA.getMerchantPublicId(), orderB.getPublicId())).isEmpty();
        assertThat(lookup.resolveMerchant(UUID.randomUUID())).isEmpty();
    }

    @Test
    void lookupApiCannotAcceptAnExternalTenantOverride() {
        assertThat(Arrays.stream(AndroidPublicIdentityLookupService.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("resolve"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .doesNotContain(Long.class, long.class, String.class);
    }

    private Tenant tenant(String label, String suffix) {
        Tenant tenant = new Tenant();
        tenant.setNome("Merchant " + label);
        tenant.setSlug("merchant-" + label.toLowerCase() + "-" + suffix);
        tenant.setTenantCode((label + suffix).toUpperCase());
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenant;
    }

    private CategoriaProduto category(Tenant tenant, String label, String suffix) {
        CategoriaProduto category = new CategoriaProduto();
        category.setTenant(tenant);
        category.setNome("Categoria " + label);
        category.setSlug("categoria-" + label.toLowerCase() + "-" + suffix);
        category.setOrdem(1);
        category.setAtivo(true);
        return category;
    }

    private Produto product(Tenant tenant, CategoriaProduto category, String label, String suffix) {
        Produto product = new Produto();
        product.setTenant(tenant);
        product.setCategoriaProduto(category);
        product.setCategoria(CategoriaProdutoLegacy.OUTROS);
        product.setCodigo("PROD-" + label + "-" + suffix);
        product.setNome("Produto " + label);
        product.setPreco(new BigDecimal("100.00"));
        product.setAtivo(true);
        product.setDisponivel(true);
        return product;
    }

    private Pedido order(Tenant tenant, String label, String suffix) {
        Pedido order = new Pedido();
        order.setTenant(tenant);
        order.setNumero("PID-" + label + "-" + suffix);
        order.setStatus(StatusPedido.CRIADO);
        order.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        order.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
        order.setTotal(new BigDecimal("100.00"));
        return order;
    }
}
