package com.restaurante.platform.discovery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.mapper.DiscoveryDtoMapper;
import com.restaurante.platform.discovery.mapper.DiscoveryEntityMapper;
import com.restaurante.platform.discovery.repository.DiscoveryScenario;
import com.restaurante.platform.discovery.repository.InMemoryDiscoveryRepository;
import com.restaurante.platform.discovery.validation.DiscoveryRequestValidator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDiscoveryServiceTest {

    private final AtomicReference<DiscoveryScenario> scenario =
            new AtomicReference<>(DiscoveryScenario.CONTENT);
    private DiscoveryService service;

    @BeforeEach
    void setUp() {
        scenario.set(DiscoveryScenario.CONTENT);
        service = new DefaultDiscoveryService(
                new InMemoryDiscoveryRepository(new DiscoveryEntityMapper(), scenario::get),
                new DiscoveryRequestValidator(),
                new DiscoveryDtoMapper());
    }

    @Test
    void homeSearchAndMerchantReturnRestResultsWithoutHttpTypes() {
        DiscoveryHomeResponse home = success(service.home(new HomeDiscoveryRequest(
                        -8.83, 13.23, null, null, null, null, null)))
                .data();
        MerchantSearchResponse search = success(service.search(new SearchDiscoveryRequest(
                        " café ", null, -8.83, 13.23, null, null, null, "name")))
                .data();
        MerchantOverviewResponse merchant = success(
                        service.merchant(new MerchantRequest(" sabor-maianga ")))
                .data();

        assertEquals(5, home.categories().size());
        assertEquals(1, search.totalCount());
        assertEquals("cafe-horizonte", search.merchants().getFirst().id());
        assertEquals("Sabor da Maianga", merchant.name());
    }

    @Test
    void validationFailureIsExplicitInvalidRequestResult() {
        DiscoveryResult.Error<?> error = assertInstanceOf(
                DiscoveryResult.Error.class,
                service.search(new SearchDiscoveryRequest(
                        "", null, null, null, null, -1, 20, "FEATURED")));

        assertEquals(DiscoveryError.INVALID_REQUEST, error.reason());
    }

    @Test
    void preservesEmptyNotFoundAndRepositoryErrors() {
        DiscoveryResult<?> noMatch = service.search(new SearchDiscoveryRequest(
                "not present", null, null, null, null, 0, 20, "NAME"));
        assertInstanceOf(DiscoveryResult.Empty.class, noMatch);

        DiscoveryResult.Error<?> notFound = assertInstanceOf(
                DiscoveryResult.Error.class,
                service.merchant(new MerchantRequest("missing")));
        assertEquals(DiscoveryError.NOT_FOUND, notFound.reason());

        scenario.set(DiscoveryScenario.ERROR);
        DiscoveryResult.Error<?> error = assertInstanceOf(
                DiscoveryResult.Error.class,
                service.home(new HomeDiscoveryRequest(null, null, null, null, 0, 20, null)));
        assertEquals(DiscoveryError.UNKNOWN, error.reason());

        scenario.set(DiscoveryScenario.OFFLINE);
        DiscoveryResult.Error<?> unavailable = assertInstanceOf(
                DiscoveryResult.Error.class,
                service.merchant(new MerchantRequest("sabor-maianga")));
        assertEquals(DiscoveryError.SERVICE_UNAVAILABLE, unavailable.reason());
        assertTrue(unavailable.message().contains("indisponível"));
    }

    @SuppressWarnings("unchecked")
    private <T> DiscoveryResult.Success<T> success(DiscoveryResult<T> result) {
        return (DiscoveryResult.Success<T>) assertInstanceOf(DiscoveryResult.Success.class, result);
    }
}
