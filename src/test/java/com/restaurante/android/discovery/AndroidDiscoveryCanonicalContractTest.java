package com.restaurante.android.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.android.api.AndroidPublicApiController;
import com.restaurante.android.discovery.controller.AndroidDiscoveryController;
import com.restaurante.android.discovery.dto.AndroidDiscoveryHomeResponse;
import com.restaurante.android.discovery.dto.AndroidDiscoverySearchResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantDetailResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantSummaryResponse;
import com.restaurante.android.discovery.security.AndroidDiscoveryPublicPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AndroidDiscoveryCanonicalContractTest {

    private static final Path CONTRACT = Path.of(
            "docs/contracts/android/CONSUMA_AQUI_ANDROID_V1.contract.json");

    @Test
    void branchDefinesOnlyTheThreeCanonicalPluralPublicHandlers() {
        assertThat(AndroidDiscoveryController.class.isAnnotationPresent(AndroidPublicApiController.class))
                .isTrue();
        assertThat(AndroidDiscoveryController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/v1/discovery");
        assertThat(Arrays.stream(AndroidDiscoveryController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())))
                .containsExactlyInAnyOrder("/home", "/search", "/merchants/{merchantId}")
                .noneMatch(path -> path.startsWith("/merchant/"));

        assertThat(matches("GET", "/api/v1/discovery/merchants/9f4b774a-5f11-4e8e-9a1f-30b53eb6db68"))
                .isTrue();
        assertThat(matches("GET", "/api/v1/discovery/merchant/9f4b774a-5f11-4e8e-9a1f-30b53eb6db68"))
                .isFalse();
        assertThat(matches("POST", "/api/v1/discovery/home")).isFalse();
    }

    @Test
    void officialManifestRemainsFrozenUntilControlledMerge() throws Exception {
        JsonNode contract = new ObjectMapper().readTree(Files.readString(CONTRACT));
        List<JsonNode> endpoints = contract.path("endpoints").findValues("implementationStatus");
        assertThat(endpoints.stream().map(JsonNode::asText).toList())
                .containsExactly(
                        "HISTORICAL_BRANCH_ONLY", "HISTORICAL_BRANCH_ONLY", "HISTORICAL_BRANCH_ONLY",
                        "NOT_IMPLEMENTED", "NOT_IMPLEMENTED", "NOT_IMPLEMENTED",
                        "NOT_IMPLEMENTED", "NOT_IMPLEMENTED", "NOT_IMPLEMENTED");
    }

    @Test
    void publicDiscoveryDtosCannotExposeInternalIdentity() {
        List<String> forbidden = List.of(
                "id", "tenantId", "businessAccountId", "institutionId", "unitId", "entity", "table");
        for (Class<?> type : List.of(
                AndroidDiscoveryHomeResponse.class,
                AndroidDiscoverySearchResponse.class,
                AndroidMerchantSummaryResponse.class,
                AndroidMerchantDetailResponse.class)) {
            assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList())
                    .doesNotContainAnyElementsOf(forbidden);
        }
    }

    private boolean matches(String method, String uri) {
        return AndroidDiscoveryPublicPaths.matches(new MockHttpServletRequest(method, uri));
    }
}
