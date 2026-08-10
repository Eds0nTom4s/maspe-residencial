package com.restaurante.contract.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConsumaAquiAndroidV1ContractTest {

    private static final Path CONTRACT = Path.of("docs/contracts/android/CONSUMA_AQUI_ANDROID_V1.contract.json");
    private static final Path OPENAPI = Path.of("docs/contracts/android/CONSUMA_AQUI_ANDROID_V1.openapi.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode contract;
    private static JsonNode openApi;

    @BeforeAll
    static void loadDocuments() throws IOException {
        contract = MAPPER.readTree(CONTRACT.toFile());
        openApi = MAPPER.readTree(OPENAPI.toFile());
    }

    @Test
    void freezesExactlyNineUniqueOperations() {
        assertEquals(9, contract.path("endpoints").size());
        assertEquals(9, openApi.path("paths").size());

        Set<String> operations = new HashSet<>();
        contract.path("endpoints").forEach(endpoint -> assertTrue(operations.add(
                endpoint.path("method").asText() + " " + endpoint.path("path").asText())));
        assertEquals(9, operations.size());
    }

    @Test
    void singularDiscoveryDetailIsNotCanonical() {
        assertFalse(openApi.path("paths").has("/discovery/merchant/{merchantId}"));
        assertTrue(openApi.path("paths").has("/discovery/merchants/{merchantId}"));
    }

    @Test
    void publicIdentifiersAreUuidAndNeverLong() {
        assertEquals("UUID_V4_LOWERCASE", contract.at("/ids/publicType").asText());
        assertFalse(contract.at("/ids/internalPrimaryKeysExposed").asBoolean());
        assertEquals("uuid", openApi.at("/components/schemas/PublicId/format").asText());

        for (String parameter : Set.of("MerchantId", "ProductId", "OrderId")) {
            assertEquals("#/components/schemas/PublicId",
                    openApi.at("/components/parameters/" + parameter + "/schema/$ref").asText());
        }
    }

    @Test
    void androidCannotSupplyTenantOrFinancialAuthority() throws IOException {
        String manifest = Files.readString(CONTRACT);
        assertTrue(contract.at("/orders/forbiddenRequestFields").toString().contains("tenantId"));
        assertTrue(contract.at("/orders/forbiddenRequestFields").toString().contains("total"));
        assertFalse(openApi.at("/components/schemas/CreateOrderRequest/properties").has("tenantId"));
        assertFalse(openApi.at("/components/schemas/CreateOrderRequest/properties").has("total"));
        assertTrue(manifest.contains("SERVER_FROM_QUOTE"));
    }

    @Test
    void paymentRequiresAcceptanceInContractAndCurrentMainGuard() throws IOException {
        assertEquals("UNACCEPTED_ORDER_HAS_NO_PAYMENT_ORDER",
                contract.at("/states/paymentInvariant").asText());
        assertEquals("NOT_PAYABLE", contract.at("/orders/initialFinancialStatus").asText());
        assertFalse(contract.at("/orders/initialPaymentAvailable").asBoolean());

        String paymentService = Files.readString(Path.of(
                "src/main/java/com/restaurante/financeiro/service/OrdemPagamentoService.java"));
        assertTrue(paymentService.contains("pedido.getStatus() != StatusPedido.EM_ANDAMENTO"));
        assertTrue(paymentService.contains("só pode ser gerada após aceite"));
    }

    @Test
    void implementedStatusRequiresAnExplicitRealMainHandler() throws IOException {
        Path discoveryController = Path.of(
                "src/main/java/com/restaurante/android/discovery/controller/AndroidDiscoveryController.java");
        Map<String, String> realMainMappings = Map.of(
                "GET /api/v1/discovery/home", "@GetMapping(\"/home\")",
                "GET /api/v1/discovery/search", "@GetMapping(\"/search\")",
                "GET /api/v1/discovery/merchants/{merchantId}",
                        "@GetMapping(\"/merchants/{merchantId}\")");
        assertTrue(Files.isRegularFile(discoveryController));
        String handlerSource = Files.readString(discoveryController);
        assertTrue(handlerSource.contains("@RequestMapping(\"/v1/discovery\")"));

        for (JsonNode endpoint : contract.path("endpoints")) {
            if (!"IMPLEMENTED".equals(endpoint.path("implementationStatus").asText())) {
                continue;
            }
            String key = endpoint.path("method").asText() + " " + endpoint.path("path").asText();
            String mapping = realMainMappings.get(key);
            assertNotNull(mapping, "IMPLEMENTED without audited main handler: " + key);
            assertTrue(handlerSource.contains(mapping), "Missing main handler mapping: " + mapping);
        }
        assertEquals(3, realMainMappings.size());
    }

    @Test
    void openApiStatusesMatchManifestAndControlledVocabulary() {
        Set<String> allowed = Set.of("IMPLEMENTED", "PARTIAL", "NOT_IMPLEMENTED", "HISTORICAL_BRANCH_ONLY");
        contract.path("endpoints").forEach(endpoint -> {
            String fullPath = endpoint.path("path").asText();
            String relativePath = fullPath.substring("/api/v1".length());
            String method = endpoint.path("method").asText().toLowerCase();
            String manifestStatus = endpoint.path("implementationStatus").asText();
            String openApiStatus = openApi.path("paths").path(relativePath).path(method)
                    .path("x-consuma-implementation-status").asText();
            assertTrue(allowed.contains(manifestStatus));
            assertEquals(manifestStatus, openApiStatus, endpoint.path("path").asText());
        });
    }

    @Test
    void identityAndMoneyFoundationExistsWithOnlyDiscoveryPromoted() throws IOException {
        Map<String, Long> statuses = contract.path("endpoints").findValues("implementationStatus").stream()
                .collect(java.util.stream.Collectors.groupingBy(JsonNode::asText, java.util.stream.Collectors.counting()));
        assertEquals(3L, statuses.getOrDefault("IMPLEMENTED", 0L));
        assertEquals(0L, statuses.getOrDefault("PARTIAL", 0L));
        assertEquals(0L, statuses.getOrDefault("HISTORICAL_BRANCH_ONLY", 0L));
        assertEquals(6L, statuses.getOrDefault("NOT_IMPLEMENTED", 0L));

        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/db/migration/V20260806_01__android_public_identity_foundation.sql")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/java/com/restaurante/android/foundation/identity/AndroidPublicIdentityLookupService.java")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/java/com/restaurante/android/foundation/money/AndroidMoneyConverter.java")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/java/com/restaurante/android/api/error/AndroidPublicErrorEnvelope.java")));
    }

    @Test
    void foundationDoesNotAddTenantOverrideOrFloatingPointMoney() throws IOException {
        String lookup = Files.readString(Path.of(
                "src/main/java/com/restaurante/android/foundation/identity/AndroidPublicIdentityLookupService.java"));
        String money = Files.readString(Path.of(
                "src/main/java/com/restaurante/android/foundation/money/AndroidMoneyConverter.java"));

        assertFalse(lookup.contains("Long tenantId,"));
        assertFalse(lookup.contains("findFirst"));
        assertFalse(money.contains("doubleValue"));
        assertFalse(money.contains("floatValue"));
        assertFalse(money.contains("Math.round"));
        assertTrue(money.contains("RoundingMode.UNNECESSARY"));
        assertTrue(money.contains("longValueExact"));
    }
}
