package com.restaurante.platform.discovery.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DiscoveryOpenApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatedOpenApiPublishesExactV1ContractCapabilitiesAndExamples() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode openApi = objectMapper.readTree(document);

        JsonNode homeGet = operation(openApi, "/v1/discovery/home");
        JsonNode searchGet = operation(openApi, "/v1/discovery/search");
        JsonNode merchantGet = operation(openApi, "/v1/discovery/merchant/{merchantId}");

        assertPublicGetOnly(openApi, "/v1/discovery/home", homeGet);
        assertPublicGetOnly(openApi, "/v1/discovery/search", searchGet);
        assertPublicGetOnly(openApi, "/v1/discovery/merchant/{merchantId}", merchantGet);

        assertEquals("0", parameter(searchGet, "page").at("/schema/default").asText());
        assertEquals("integer", parameter(searchGet, "page").at("/schema/type").asText());
        assertEquals(0, parameter(searchGet, "page").at("/schema/minimum").asInt());
        assertEquals("20", parameter(searchGet, "pageSize").at("/schema/default").asText());
        assertEquals(100, parameter(searchGet, "pageSize").at("/schema/maximum").asInt());
        assertEquals("NAME", parameter(searchGet, "sort").at("/schema/default").asText());
        assertTrue(parameter(searchGet, "sort").path("description").asText().contains("Somente NAME"));

        assertEquals(
                Set.of("NAME", "FEATURED", "NEAREST", "TOP_RATED", "MOST_POPULAR"),
                values(parameter(searchGet, "sort").at("/schema/enum")));
        assertEquals(100, parameter(searchGet, "query").at("/schema/maxLength").asInt());
        assertEquals("number", parameter(searchGet, "latitude").at("/schema/type").asText());
        assertEquals(120, parameter(searchGet, "categoryId").at("/schema/maxLength").asInt());
        assertEquals(120, parameter(searchGet, "municipalityId").at("/schema/maxLength").asInt());

        assertEquals(
                "no-store",
                searchGet.at("/responses/200/headers/Cache-Control/schema/example").asText());
        assertEquals(
                "SORT_NOT_SUPPORTED",
                exampleCode(searchGet, "400", "sort sem suporte"));
        assertEquals(
                "SERVICE_UNAVAILABLE",
                exampleCode(searchGet, "503", null));
        assertEquals("INVALID_REQUEST", exampleCode(searchGet, "400", "request inválido"));
        assertEquals("UNKNOWN", exampleCode(searchGet, "500", null));
        assertEquals("NOT_FOUND", exampleCode(merchantGet, "404", null));

        assertTrue(example(searchGet, "200", "com dados").path("merchants").isArray());
        assertTrue(example(searchGet, "200", "vazio").path("merchants").isEmpty());
        assertTrue(example(homeGet, "200", "com dados").path("recommended").path("items").isArray());
        assertTrue(example(homeGet, "200", "vazio").path("categories").isEmpty());
        assertEquals("sabor-maianga", example(merchantGet, "200", null).path("id").asText());

        JsonNode required = openApi.at("/components/schemas/MerchantSummaryDto/required");
        assertTrue(values(required).containsAll(
                Set.of("id", "name", "category", "availability", "fulfillmentOptions", "catalogAvailable")));
        String discoverySchemas = Stream.of(
                        "MerchantSummaryDto",
                        "MerchantOverviewResponse",
                        "MerchantSearchResponse",
                        "DiscoveryHomeResponse")
                .map(name -> openApi.path("components").path("schemas").path(name).toString())
                .collect(java.util.stream.Collectors.joining());
        assertFalse(discoverySchemas.contains("tenantId"));
        assertFalse(discoverySchemas.contains("ownerId"));
        assertFalse(openApi.at("/components/schemas/DiscoveryHomeResponse/properties").has("capabilities"));
    }

    private JsonNode operation(JsonNode openApi, String path) {
        JsonNode operation = openApi.path("paths").path(path).path("get");
        assertFalse(operation.isMissingNode(), "missing GET " + path);
        return operation;
    }

    private void assertPublicGetOnly(JsonNode openApi, String path, JsonNode getOperation) {
        JsonNode pathItem = openApi.path("paths").path(path);
        assertEquals(Set.of("get"), values(pathItem));
        JsonNode security = getOperation.path("security");
        assertTrue(security.isArray());
        assertTrue(security.isEmpty());
        assertFalse(getOperation.path("responses").path("200").isMissingNode());
    }

    private JsonNode parameter(JsonNode operation, String name) {
        return StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .filter(parameter -> name.equals(parameter.path("name").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing parameter " + name));
    }

    private String exampleCode(JsonNode operation, String status, String exampleName)
            throws Exception {
        JsonNode mediaType = mediaType(operation, status);
        JsonNode payload = examplePayload(mediaType, exampleName);
        if (!payload.has("code")) {
            throw new AssertionError("OpenAPI example without code: " + mediaType.toPrettyString());
        }
        return payload.path("code").asText();
    }

    private JsonNode example(JsonNode operation, String status, String exampleName)
            throws Exception {
        return examplePayload(mediaType(operation, status), exampleName);
    }

    private JsonNode mediaType(JsonNode operation, String status) {
        JsonNode content = operation.path("responses").path(status).path("content");
        if (!content.isObject() || content.isEmpty()) {
            throw new AssertionError("OpenAPI response without content: " + status);
        }
        return content.elements().next();
    }

    private JsonNode examplePayload(JsonNode mediaType, String exampleName) throws Exception {
        JsonNode examples = mediaType.path("examples");
        JsonNode value;
        if (examples.isObject() && !examples.isEmpty()) {
            JsonNode example = exampleName == null
                    ? examples.elements().next()
                    : examples.path(exampleName);
            value = example.path("value");
        } else {
            value = mediaType.path("example");
        }
        JsonNode payload = value.isTextual() ? objectMapper.readTree(value.asText()) : value;
        if (payload.isMissingNode() || payload.isNull()) {
            throw new AssertionError("OpenAPI response without example: " + mediaType.toPrettyString());
        }
        return payload;
    }

    private Set<String> values(JsonNode node) {
        java.util.HashSet<String> values = new java.util.HashSet<>();
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(values::add);
        } else {
            node.forEach(value -> values.add(value.asText()));
        }
        return Set.copyOf(values);
    }
}
