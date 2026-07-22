package com.restaurante.platform.discovery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.mapper.DiscoveryEntityMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryDiscoveryRepositoryTest {

    private final AtomicReference<DiscoveryScenario> scenario =
            new AtomicReference<>(DiscoveryScenario.CONTENT);
    private InMemoryDiscoveryRepository repository;

    @BeforeEach
    void setUp() {
        scenario.set(DiscoveryScenario.CONTENT);
        repository = new InMemoryDiscoveryRepository(new DiscoveryEntityMapper(), scenario::get);
    }

    @Test
    void homePreservesAndroidFixturesAndCuratedSections() {
        HomeDiscoveryContent content = success(repository.home(homeRequest(true))).data();

        assertEquals(5, content.categories().size());
        assertEquals(4, content.nearby().items().size());
        assertTrue(content.nearby().hasMore());
        assertTrue(content.recommended().items().isEmpty());
        assertEquals(3, content.featured().items().size());
        assertTrue(content.featured().items().stream()
                .allMatch(item -> Boolean.TRUE.equals(item.featured())));
        assertEquals(
                List.of(
                        "sabor-maianga",
                        "doce-embondeiro",
                        "cafe-horizonte",
                        "mercado-talatona",
                        "servicos-viana",
                        "cantinho-kilamba",
                        "fonte-fresca",
                        "paes-mutamba"),
                repository.merchantIds());
    }

    @Test
    void homeWithoutLocationReturnsRecommendationsAndNoInventedDistance() {
        HomeDiscoveryContent content = success(repository.home(homeRequest(false))).data();

        assertTrue(content.nearby().items().isEmpty());
        assertFalse(content.recommended().items().isEmpty());
        assertTrue(content.recommended().items().stream()
                .allMatch(item -> item.location().distanceMeters() == null));
    }

    @Test
    void searchTrimsAccentsFiltersSortsAndPaginatesZeroBased() {
        MerchantSearchContent cafe = success(repository.search(searchRequest(
                        "  CAFÉ HORIZONTE ", null, 0, 20, "NAME", true)))
                .data();
        assertEquals(List.of("cafe-horizonte"), ids(cafe));

        MerchantSearchContent bakery = success(repository.search(searchRequest(
                        "pastelaria", "bakery", 0, 20, "TOP_RATED", true)))
                .data();
        assertEquals(List.of("paes-mutamba", "doce-embondeiro"), ids(bakery));

        MerchantSearchContent page = success(repository.search(searchRequest(
                        "", null, 1, 3, "FEATURED", true)))
                .data();
        assertEquals(1, page.page());
        assertEquals(3, page.pageSize());
        assertEquals(8, page.totalCount());
        assertTrue(page.hasMore());
        assertEquals(3, page.merchants().size());
    }

    @Test
    void noMatchIsExplicitEmptyWithMetadata() {
        DiscoveryResult.Empty<MerchantSearchContent> result = assertInstanceOf(
                DiscoveryResult.Empty.class,
                repository.search(searchRequest("não existe", null, 0, 20, "NAME", true)));

        assertEquals(0, result.data().totalCount());
        assertTrue(result.data().merchants().isEmpty());
    }

    @Test
    void merchantOverviewPreservesSharedAndOptionalFacts() {
        MerchantOverview complete = success(
                        repository.merchant(new MerchantRequest("sabor-maianga")))
                .data();
        assertEquals("Sabor da Maianga", complete.name());
        assertEquals("restaurant", complete.category().id());
        assertEquals(4.7, complete.rating().value());
        assertEquals("almoco-local", complete.promotion().id());
        assertTrue(complete.catalogAvailable());

        MerchantOverview partial = success(
                        repository.merchant(new MerchantRequest("servicos-viana")))
                .data();
        assertNull(partial.contact());
        assertNull(partial.schedule());
        assertNull(partial.promotion());
        assertFalse(partial.catalogAvailable());
    }

    @Test
    void unknownAndEmptyMerchantAreExplicitNotFound() {
        assertError(
                repository.merchant(new MerchantRequest("missing")), DiscoveryError.NOT_FOUND);
        scenario.set(DiscoveryScenario.EMPTY);
        assertError(
                repository.merchant(new MerchantRequest("sabor-maianga")),
                DiscoveryError.NOT_FOUND);
    }

    @Test
    void everyEndpointSupportsEmptyErrorAndOfflineScenarios() {
        scenario.set(DiscoveryScenario.EMPTY);
        assertInstanceOf(DiscoveryResult.Empty.class, repository.home(homeRequest(true)));
        assertInstanceOf(DiscoveryResult.Empty.class, repository.search(searchRequest(
                "", null, 0, 20, "FEATURED", true)));

        scenario.set(DiscoveryScenario.ERROR);
        assertError(repository.home(homeRequest(true)), DiscoveryError.UNKNOWN);
        assertError(
                repository.search(searchRequest("", null, 0, 20, "FEATURED", true)),
                DiscoveryError.UNKNOWN);
        assertError(
                repository.merchant(new MerchantRequest("sabor-maianga")),
                DiscoveryError.UNKNOWN);

        scenario.set(DiscoveryScenario.OFFLINE);
        assertError(repository.home(homeRequest(true)), DiscoveryError.SERVICE_UNAVAILABLE);
        assertError(
                repository.search(searchRequest("", null, 0, 20, "FEATURED", true)),
                DiscoveryError.SERVICE_UNAVAILABLE);
        assertError(
                repository.merchant(new MerchantRequest("sabor-maianga")),
                DiscoveryError.SERVICE_UNAVAILABLE);
    }

    @SuppressWarnings("unchecked")
    private <T> DiscoveryResult.Success<T> success(DiscoveryResult<T> result) {
        return (DiscoveryResult.Success<T>) assertInstanceOf(DiscoveryResult.Success.class, result);
    }

    private void assertError(DiscoveryResult<?> result, DiscoveryError expected) {
        DiscoveryResult.Error<?> error = assertInstanceOf(DiscoveryResult.Error.class, result);
        assertEquals(expected, error.reason());
    }

    private List<String> ids(MerchantSearchContent content) {
        return content.merchants().stream().map(item -> item.id()).toList();
    }

    private HomeDiscoveryRequest homeRequest(boolean withLocation) {
        return new HomeDiscoveryRequest(
                withLocation ? -8.83 : null,
                withLocation ? 13.23 : null,
                null,
                null,
                0,
                20,
                "FEATURED");
    }

    private SearchDiscoveryRequest searchRequest(
            String query,
            String category,
            int page,
            int pageSize,
            String sort,
            boolean withLocation) {
        return new SearchDiscoveryRequest(
                query,
                category,
                withLocation ? -8.83 : null,
                withLocation ? 13.23 : null,
                null,
                page,
                pageSize,
                sort);
    }
}
