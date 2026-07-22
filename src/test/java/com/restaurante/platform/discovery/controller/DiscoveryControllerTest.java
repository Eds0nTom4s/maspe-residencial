package com.restaurante.platform.discovery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAvailabilityDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.exception.DiscoveryExceptionHandler;
import com.restaurante.platform.discovery.service.DiscoveryService;
import com.restaurante.platform.discovery.validation.DiscoveryHttpParameterValidator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiscoveryControllerTest {

    private DiscoveryService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DiscoveryService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new DiscoveryController(service, new DiscoveryHttpParameterValidator()))
                .setControllerAdvice(new DiscoveryExceptionHandler())
                .build();
    }

    @Test
    void homeReturnsJsonContract() throws Exception {
        DiscoveryHomeResponse response = new DiscoveryHomeResponse(
                List.of(new MerchantCategoryDto("restaurant", "Restaurantes")),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false));
        when(service.home(any(HomeDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Success<>(response));

        mvc.perform(get("/v1/discovery/home")
                        .param("latitude", "-8.83")
                        .param("longitude", "13.23")
                        .param("page", "0")
                        .param("pageSize", "20")
                        .param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.categories[0].id").value("restaurant"))
                .andExpect(jsonPath("$.nearby.items").isArray())
                .andExpect(jsonPath("$.recommended.hasMore").value(false));
    }

    @Test
    void emptySearchIsSuccessfulAndSerializesPagingMetadata() throws Exception {
        MerchantSearchResponse response =
                new MerchantSearchResponse(List.of(), List.of(), 0, 20, 0, false);
        when(service.search(any(SearchDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Empty<>(response));

        mvc.perform(get("/v1/discovery/search").param("query", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchants").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void emptyHomeReturnsAllStableSectionsAsEmptyArrays() throws Exception {
        DiscoveryHomeResponse response = new DiscoveryHomeResponse(
                List.of(),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false),
                new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                        List.of(), false));
        when(service.home(any(HomeDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Empty<>(response));

        mvc.perform(get("/v1/discovery/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.nearby.items").isEmpty())
                .andExpect(jsonPath("$.recommended.items").isEmpty())
                .andExpect(jsonPath("$.featured.items").isEmpty());
    }

    @Test
    void merchantSerializesOverviewAndNullOptionalFields() throws Exception {
        MerchantOverviewResponse response = new MerchantOverviewResponse(
                "servicos-viana",
                "Serviços Viana",
                "Assistência",
                "Assistência demonstrativa",
                new MerchantCategoryDto("services", "Serviços"),
                null,
                null,
                new MerchantAvailabilityDto("UNKNOWN", null, null),
                Set.of("SERVICE"),
                null,
                null,
                null,
                null,
                null,
                false);
        when(service.merchant(any(MerchantRequest.class)))
                .thenReturn(new DiscoveryResult.Success<>(response));

        mvc.perform(get("/v1/discovery/merchant/servicos-viana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("servicos-viana"))
                .andExpect(jsonPath("$.availability.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.fulfillmentOptions[0]").value("SERVICE"))
                .andExpect(jsonPath("$.catalogAvailable").value(false))
                .andExpect(jsonPath("$.contact").doesNotExist());
    }

    @Test
    void invalidRequestResultReturnsStandardized400Payload() throws Exception {
        when(service.search(any(SearchDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Error<>(
                        DiscoveryError.INVALID_REQUEST, "page deve ser maior ou igual a 0."));

        mvc.perform(get("/v1/discovery/search").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("page deve ser maior ou igual a 0."))
                .andExpect(jsonPath("$.path").value("/v1/discovery/search"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedQueryParameterReturns400WithoutLeakingException() throws Exception {
        mvc.perform(get("/v1/discovery/search").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedOrRepeatedParametersReturn400InsteadOfBeingIgnored() throws Exception {
        mvc.perform(get("/v1/discovery/search").param("onlyOpen", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Parâmetro não suportado no contrato v1: onlyOpen."));

        mvc.perform(get("/v1/discovery/search").param("page", "0", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Parâmetro repetido no contrato v1: page."));
    }

    @Test
    void featuredIsExplicitlyUnsupportedWithoutNameFallback() throws Exception {
        when(service.search(any(SearchDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Error<>(
                        DiscoveryError.SORT_NOT_SUPPORTED,
                        "sort 'FEATURED' ainda não possui fonte persistente suportada."));

        mvc.perform(get("/v1/discovery/search").param("sort", "FEATURED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SORT_NOT_SUPPORTED"));
    }

    @Test
    void missingMerchantReturnsStandardized404Payload() throws Exception {
        when(service.merchant(any(MerchantRequest.class)))
                .thenReturn(new DiscoveryResult.Error<>(
                        DiscoveryError.NOT_FOUND, "Comerciante 'missing' não encontrado."));

        mvc.perform(get("/v1/discovery/merchant/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    static Stream<Arguments> serverErrors() {
        return Stream.of(
                Arguments.of(
                        DiscoveryError.SERVICE_UNAVAILABLE,
                        "Serviço temporariamente indisponível.",
                        503),
                Arguments.of(DiscoveryError.UNKNOWN, "Falha desconhecida.", 500));
    }

    @ParameterizedTest
    @MethodSource("serverErrors")
    void serviceUnavailableAndUnknownReturnTheirStandardizedStatus(
            DiscoveryError reason, String message, int expectedStatus) throws Exception {
        when(service.home(any(HomeDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Error<>(reason, message));

        mvc.perform(get("/v1/discovery/home"))
                .andExpect(status().is(expectedStatus))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(reason.name()))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void unexpectedFailureReturnsUnknown500WithoutInternalDetails() throws Exception {
        when(service.home(any(HomeDiscoveryRequest.class)))
                .thenThrow(new IllegalStateException("sensitive detail"));

        mvc.perform(get("/v1/discovery/home"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("UNKNOWN"))
                .andExpect(jsonPath("$.message").value("Não foi possível concluir o Discovery."));
    }

    @Test
    void doesNotExposeAnyWriteEndpoint() throws Exception {
        mvc.perform(post("/v1/discovery/home"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/v1/discovery/home"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/v1/discovery/home"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/v1/discovery/home"))
                .andExpect(status().isMethodNotAllowed());
    }
}
