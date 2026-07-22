package com.restaurante.platform.discovery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.TenantDeliveryPolicy;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.domain.FulfillmentOption;
import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.service.DiscoveryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class PersistentDiscoveryRepositoryJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DiscoveryRepository repository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DiscoveryService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsOnlyCanonicalPublishedMerchantDataThroughJpaProjections() {
        Fixture fixture = persistPublishedMerchant();

        MerchantSearchContent search = success(repository.search(new SearchDiscoveryRequest(
                        "  Horizonte ",
                        "drinks",
                        null,
                        null,
                        "Ingombota",
                        0,
                        20,
                        "NAME")))
                .data();

        assertEquals(1, search.totalCount());
        assertEquals(fixture.slug(), search.merchants().getFirst().id());
        assertEquals("drinks", search.merchants().getFirst().category().id());
        assertEquals("https://cdn.example/banner.jpg", search.merchants().getFirst().imageUrl());
        assertNull(search.merchants().getFirst().rating());
        assertNull(search.merchants().getFirst().location().distanceMeters());
        assertTrue(search.merchants().getFirst().catalogAvailable());
        assertTrue(search.merchants().getFirst().fulfillmentOptions().containsAll(
                java.util.Set.of(
                        FulfillmentOption.DELIVERY,
                        FulfillmentOption.PICKUP,
                        FulfillmentOption.DINE_IN)));

        MerchantOverview overview =
                success(repository.merchant(new MerchantRequest(fixture.slug()))).data();
        assertEquals("Bar Horizonte", overview.name());
        assertEquals("Bar e cafetaria", overview.fullDescription());
        assertEquals("https://cdn.example/logo.jpg", overview.logoUrl());
        assertEquals("Ingombota — Luanda", overview.address().displayName());
        assertNull(overview.contact());
        assertTrue(overview.catalogAvailable());
        assertNull(overview.schedule());
        assertNull(overview.promotion());
    }

    @Test
    void publicationPolicyExcludesUnpublishedMerchantFromListAndDetail() {
        Fixture fixture = persistPublishedMerchant();
        fixture.cardapio().setCardapioPublicado(false);
        entityManager.flush();
        entityManager.clear();

        DiscoveryResult<MerchantSearchContent> search = repository.search(new SearchDiscoveryRequest(
                "", null, null, null, null, 0, 20, "NAME"));
        assertInstanceOf(DiscoveryResult.Empty.class, search);

        DiscoveryResult.Error<?> detail = assertInstanceOf(
                DiscoveryResult.Error.class,
                repository.merchant(new MerchantRequest(fixture.slug())));
        assertEquals(DiscoveryError.NOT_FOUND, detail.reason());
    }

    @Test
    void publicationPolicyRequiresActiveAccountTenantInstitutionAndUnit() {
        Fixture inactiveAccount = persistPublishedMerchant();
        inactiveAccount.account().setEstado(BusinessAccountEstado.SUSPENSA);

        Fixture inactiveTenant = persistPublishedMerchant();
        inactiveTenant.tenant().setEstado(TenantEstado.SUSPENSO);

        Fixture inactiveInstitution = persistPublishedMerchant();
        inactiveInstitution.institution().setAtiva(false);

        Fixture inactiveUnit = persistPublishedMerchant();
        inactiveUnit.unit().setAtiva(false);

        entityManager.flush();
        entityManager.clear();

        DiscoveryResult<MerchantSearchContent> search = repository.search(new SearchDiscoveryRequest(
                "", null, null, null, null, 0, 20, "NAME"));
        assertInstanceOf(DiscoveryResult.Empty.class, search);
    }

    @Test
    void publicationPolicyRejectsBlankPublicNameAndMissingOperationalLocation() {
        Fixture blankName = persistPublishedMerchant();
        blankName.tenant().setNome("   ");

        Fixture noLocation = persistPublishedMerchant();
        entityManager.remove(noLocation.unit());
        entityManager.remove(noLocation.institution());
        entityManager.flush();
        entityManager.clear();

        DiscoveryResult<MerchantSearchContent> search = repository.search(new SearchDiscoveryRequest(
                "", null, null, null, null, 0, 20, "NAME"));
        assertInstanceOf(DiscoveryResult.Empty.class, search);
        assertEquals(
                DiscoveryError.NOT_FOUND,
                assertInstanceOf(
                                DiscoveryResult.Error.class,
                                repository.merchant(new MerchantRequest(noLocation.slug())))
                        .reason());
    }

    @Test
    void realQueryAppliesStablePagingAndTotalCount() {
        java.util.List<Fixture> fixtures = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            Fixture fixture = persistPublishedMerchant();
            fixture.tenant().setNome("Mesmo Nome");
            fixtures.add(fixture);
        }
        entityManager.flush();
        entityManager.clear();

        java.util.List<String> observedIds = new java.util.ArrayList<>();
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            MerchantSearchContent page = success(repository.search(new SearchDiscoveryRequest(
                            "", "drinks", null, null, null, pageNumber, 2, "NAME")))
                    .data();
            observedIds.addAll(page.merchants().stream().map(item -> item.id()).toList());
            assertEquals(5, page.totalCount());
            assertEquals(pageNumber < 2, page.hasMore());
        }

        assertEquals(5, observedIds.size());
        assertEquals(5, new java.util.HashSet<>(observedIds).size());
        assertEquals(observedIds.stream().sorted().toList(), observedIds);

        DiscoveryResult.Empty<MerchantSearchContent> beyond = assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(new SearchDiscoveryRequest(
                        "", "drinks", null, null, null, 3, 2, "NAME")));
        assertEquals(5, beyond.data().totalCount());
        assertTrue(beyond.data().merchants().isEmpty());
    }

    @Test
    void catalogAvailabilityRequiresPublishedActiveProductAndCategory() {
        Fixture fixture = persistPublishedMerchant(false);

        MerchantSearchContent search = success(repository.search(new SearchDiscoveryRequest(
                        "", "drinks", null, null, null, 0, 20, "NAME")))
                .data();
        assertEquals(java.util.List.of(fixture.slug()),
                search.merchants().stream().map(item -> item.id()).toList());
        assertTrue(!search.merchants().getFirst().catalogAvailable());

        MerchantOverview emptyCatalog =
                success(repository.merchant(new MerchantRequest(fixture.slug()))).data();
        assertTrue(!emptyCatalog.catalogAvailable());

        Produto product = persistProduct(fixture.tenant(), fixture.productCategory());
        product.setAtivo(false);
        entityManager.flush();
        entityManager.clear();
        MerchantOverview inactiveProduct =
                success(repository.merchant(new MerchantRequest(fixture.slug()))).data();
        assertTrue(!inactiveProduct.catalogAvailable());
    }

    @Test
    void missingPublicAddressRemainsOptionalInsteadOfProducingInventedValues() {
        Fixture fixture = persistPublishedMerchant();
        fixture.fiscal().setMunicipality(null);
        fixture.fiscal().setProvince(null);
        fixture.fiscal().setAddress(null);
        entityManager.flush();
        entityManager.clear();

        MerchantSearchContent search = success(repository.search(new SearchDiscoveryRequest(
                        "", "drinks", null, null, null, 0, 20, "NAME")))
                .data();
        assertNull(search.merchants().getFirst().location());

        MerchantOverview detail =
                success(repository.merchant(new MerchantRequest(fixture.slug()))).data();
        assertNull(detail.address());
        assertNull(detail.contact());
    }

    @Test
    void filtersAreCombinedWithAndAndLikeWildcardsRemainLiteral() {
        Fixture cafe = persistPublishedMerchant();
        cafe.tenant().setNome("Café Órbita % _");
        Fixture otherMunicipality = persistPublishedMerchant();
        otherMunicipality.tenant().setNome("Café Órbita Sul");
        otherMunicipality.fiscal().setMunicipality("Talatona");
        entityManager.flush();
        entityManager.clear();

        MerchantSearchContent exact = success(repository.search(new SearchDiscoveryRequest(
                        "Café", "drinks", null, null, "Ingombota", 0, 20, "NAME")))
                .data();
        assertEquals(java.util.List.of(cafe.slug()),
                exact.merchants().stream().map(item -> item.id()).toList());

        assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(new SearchDiscoveryRequest(
                        "%", "drinks", null, null, "Talatona", 0, 20, "NAME")));
        assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(new SearchDiscoveryRequest(
                        "' OR 1=1 --", null, null, null, null, 0, 20, "NAME")));
    }

    @Test
    void searchIsCaseInsensitiveButDoesNotRemoveAccents() {
        Fixture fixture = persistPublishedMerchant();
        fixture.tenant().setNome("Café João");
        entityManager.flush();
        entityManager.clear();

        MerchantSearchContent matching = success(repository.search(new SearchDiscoveryRequest(
                        "CAFÉ JOÃO", "drinks", null, null, "ingombota", 0, 20, "NAME")))
                .data();
        assertEquals(java.util.List.of(fixture.slug()),
                matching.merchants().stream().map(item -> item.id()).toList());

        assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(new SearchDiscoveryRequest(
                        "CAFE JOAO", "drinks", null, null, "Ingombota", 0, 20, "NAME")));
    }

    @Test
    void queryTreatsQuotesBackslashEmojiPercentAndUnderscoreLiterally() {
        Fixture fixture = persistPublishedMerchant();
        fixture.tenant().setNome("Café D'Ávila % _ \" \\ 😀");
        entityManager.flush();
        entityManager.clear();

        for (String query : java.util.List.of("%", "_", "'", "\"", "\\", "😀", "D'Ávila")) {
            MerchantSearchContent match = success(repository.search(new SearchDiscoveryRequest(
                            query, "drinks", null, null, "Ingombota", 0, 20, "NAME")))
                    .data();
            assertEquals(java.util.List.of(fixture.slug()),
                    match.merchants().stream().map(item -> item.id()).toList());
        }

        assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(new SearchDiscoveryRequest(
                        "' OR 1=1 --", null, null, null, null, 0, 20, "NAME")));
    }

    @Test
    void homeAcceptsCoordinatesForFutureCompatibilityWithoutPretendingGeoOrdering() {
        Fixture fixture = persistPublishedMerchant();

        HomeDiscoveryContent home = success(repository.home(
                        new com.restaurante.platform.discovery.dto.HomeDiscoveryRequest(
                                -8.8383, 13.2344, null, null, 0, 20, "NAME")))
                .data();

        assertTrue(home.nearby().items().isEmpty());
        assertTrue(home.featured().items().isEmpty());
        assertEquals(java.util.List.of(fixture.slug()),
                home.recommended().items().stream().map(item -> item.id()).toList());
        assertNull(home.recommended().items().getFirst().location().distanceMeters());
    }

    @Test
    void serviceDefaultsSearchToZeroBasedPageTwentyAndNameSort() {
        Fixture fixture = persistPublishedMerchant();

        MerchantSearchResponse response = success(service.search(new SearchDiscoveryRequest(
                        "", "drinks", null, null, null, null, null, null)))
                .data();

        assertEquals(0, response.page());
        assertEquals(20, response.pageSize());
        assertEquals(java.util.List.of(fixture.slug()),
                response.merchants().stream().map(item -> item.id()).toList());
    }

    @Test
    void runtimeHasOnlyPersistentDiscoveryRepositoryBean() {
        Map<String, DiscoveryRepository> beans =
                applicationContext.getBeansOfType(DiscoveryRepository.class);

        assertEquals(1, beans.size());
        assertInstanceOf(PersistentDiscoveryRepository.class, beans.values().iterator().next());
    }

    @Test
    void queryCountsStayFixedWhenSearchingTenMerchants() {
        for (int index = 0; index < 10; index++) {
            persistPublishedMerchant();
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();

        statistics.clear();
        MerchantSearchContent search = success(repository.search(new SearchDiscoveryRequest(
                        "", null, null, null, null, 0, 10, "NAME")))
                .data();
        assertEquals(10, search.merchants().size());
        long searchQueries = statistics.getPrepareStatementCount();
        assertTrue(searchQueries <= 2, statementMessage(statistics));

        statistics.clear();
        success(repository.home(new com.restaurante.platform.discovery.dto.HomeDiscoveryRequest(
                null, null, null, null, 0, 10, "NAME")));
        long homeQueries = statistics.getPrepareStatementCount();
        assertTrue(homeQueries <= 2, statementMessage(statistics));

        statistics.clear();
        success(repository.merchant(new MerchantRequest(search.merchants().getFirst().id())));
        long detailQueries = statistics.getPrepareStatementCount();
        assertTrue(detailQueries <= 2, statementMessage(statistics));
        System.out.printf(
                "DISCOVERY_QUERY_COUNT home=%d search10=%d detail=%d%n",
                homeQueries,
                searchQueries,
                detailQueries);
    }

    @Test
    void localSmokeRecordsBoundedPayloadSizesAndReadTimings() throws Exception {
        Fixture fixture = null;
        for (int index = 0; index < 10; index++) {
            fixture = persistPublishedMerchant();
        }
        entityManager.flush();
        entityManager.clear();

        long homeStart = System.nanoTime();
        Object home = success(service.home(new com.restaurante.platform.discovery.dto.HomeDiscoveryRequest(
                        null, null, null, null, 0, 10, "NAME")))
                .data();
        long homeMs = elapsedMillis(homeStart);
        long searchStart = System.nanoTime();
        Object search = success(service.search(new SearchDiscoveryRequest(
                        "", null, null, null, null, 0, 10, "NAME")))
                .data();
        long searchMs = elapsedMillis(searchStart);
        long detailStart = System.nanoTime();
        Object detail = success(service.merchant(new MerchantRequest(fixture.slug()))).data();
        long detailMs = elapsedMillis(detailStart);

        int homeBytes = objectMapper.writeValueAsBytes(home).length;
        int searchBytes = objectMapper.writeValueAsBytes(search).length;
        int detailBytes = objectMapper.writeValueAsBytes(detail).length;
        assertTrue(homeBytes < 262_144);
        assertTrue(searchBytes < 262_144);
        assertTrue(detailBytes < 65_536);
        System.out.printf(
                "DISCOVERY_SMOKE homeMs=%d homeBytes=%d searchMs=%d searchBytes=%d detailMs=%d detailBytes=%d%n",
                homeMs,
                homeBytes,
                searchMs,
                searchBytes,
                detailMs,
                detailBytes);
    }

    private Fixture persistPublishedMerchant() {
        return persistPublishedMerchant(true);
    }

    private Fixture persistPublishedMerchant(boolean withActiveProduct) {
        String suffix = Long.toString(System.nanoTime(), 36);

        BusinessAccount account = new BusinessAccount();
        account.setNome("Conta Discovery " + suffix);
        account.setSlug("conta-discovery-" + suffix);
        account.setEstado(BusinessAccountEstado.ATIVA);
        entityManager.persist(account);

        Tenant tenant = new Tenant();
        tenant.setNome("Bar Horizonte");
        tenant.setSlug("bar-horizonte-" + suffix);
        tenant.setTenantCode(("DH" + suffix).toUpperCase());
        tenant.setTipo(TenantTipo.BAR);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant.setTelefone("+244900000001");
        tenant.setEmail("bar.horizonte@example.com");
        tenant.setTemplateCode("CONSUMA_REST");
        tenant.setBusinessAccount(account);
        entityManager.persist(tenant);

        Instituicao institution = new Instituicao();
        institution.setTenant(tenant);
        institution.setNome("Bar Horizonte");
        institution.setSigla(("BH" + suffix).substring(0, 10).toUpperCase());
        institution.setNif("NIF-DISCOVERY-" + suffix);
        institution.setUrlLogo("https://cdn.example/logo.jpg");
        institution.setAtiva(true);
        institution.setTelefoneAutorizacao("+244900000001");
        entityManager.persist(institution);

        UnidadeAtendimento unit = new UnidadeAtendimento();
        unit.setNome("Bar Principal");
        unit.setTipo(TipoUnidadeAtendimento.BAR);
        unit.setAtiva(true);
        unit.setDescricao("Bar e cafetaria");
        unit.setInstituicao(institution);
        entityManager.persist(unit);

        TenantCardapioConfig cardapio = new TenantCardapioConfig();
        cardapio.setTenant(tenant);
        cardapio.setCardapioPublicado(true);
        cardapio.setUrlBanner("https://cdn.example/banner.jpg");
        entityManager.persist(cardapio);

        TenantFiscalProfile fiscal = new TenantFiscalProfile();
        fiscal.setTenant(tenant);
        fiscal.setProvince("Luanda");
        fiscal.setMunicipality("Ingombota");
        fiscal.setAddress("Rua Rainha Ginga");
        entityManager.persist(fiscal);

        CategoriaProduto productCategory = new CategoriaProduto();
        productCategory.setTenant(tenant);
        productCategory.setNome("Bebidas");
        productCategory.setSlug("bebidas");
        productCategory.setOrdem(1);
        productCategory.setAtivo(true);
        entityManager.persist(productCategory);

        if (withActiveProduct) {
            persistProduct(tenant, productCategory);
        }

        TenantDeliveryPolicy delivery = new TenantDeliveryPolicy();
        delivery.setTenant(tenant);
        delivery.setStatus(TenantDeliveryPolicyStatus.ACTIVE);
        delivery.setDeliveryEnabled(true);
        delivery.setDeliveryMode(DeliveryMode.HYBRID);
        delivery.setAllowCustomerPickup(true);
        delivery.setPreparationTimeMinutes(18);
        entityManager.persist(delivery);

        TenantOperacaoPolicy operation = new TenantOperacaoPolicy();
        operation.setTenant(tenant);
        operation.setAllowPickup(true);
        entityManager.persist(operation);

        entityManager.flush();
        return new Fixture(
                tenant.getSlug(),
                account,
                tenant,
                institution,
                unit,
                cardapio,
                fiscal,
                productCategory);
    }

    private Produto persistProduct(Tenant tenant, CategoriaProduto category) {
        Produto product = new Produto();
        product.setTenant(tenant);
        product.setCodigo("PROD-" + Long.toString(System.nanoTime(), 36));
        product.setNome("Produto Discovery");
        product.setDescricao("Produto público");
        product.setPreco(BigDecimal.valueOf(1500));
        product.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        product.setCategoriaProduto(category);
        product.setDisponivel(true);
        product.setAtivo(true);
        entityManager.persist(product);
        return product;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private String statementMessage(Statistics statistics) {
        return "unexpected statement count: " + statistics.getPrepareStatementCount();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    @SuppressWarnings("unchecked")
    private <T> DiscoveryResult.Success<T> success(DiscoveryResult<T> result) {
        return (DiscoveryResult.Success<T>) assertInstanceOf(DiscoveryResult.Success.class, result);
    }

    private record Fixture(
            String slug,
            BusinessAccount account,
            Tenant tenant,
            Instituicao institution,
            UnidadeAtendimento unit,
            TenantCardapioConfig cardapio,
            TenantFiscalProfile fiscal,
            CategoriaProduto productCategory) {}
}
