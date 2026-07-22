package com.restaurante.platform.discovery.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAvailabilityDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAddressDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantContactDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantLocationDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantPromotionDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantRatingDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSummaryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MoneyAmountDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.WeeklyScheduleDto;
import com.restaurante.platform.discovery.dto.DiscoveryErrorResponse;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest(properties = "spring.jackson.default-property-inclusion=non_null")
class DiscoveryContractTest {

    private static final Set<String> PRIVATE_FIELDS = Set.of(
            "tenantid",
            "ownerid",
            "accountid",
            "userid",
            "subscriptionid",
            "token",
            "createdby",
            "updatedby",
            "deletedby",
            "nif",
            "plano",
            "plan",
            "limites",
            "limits");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void homeAndSearchHaveStableKeysPagingTypesAndUnicode() throws Exception {
        MerchantSummaryDto summary = summary();
        DiscoveryHomeResponse home = new DiscoveryHomeResponse(
                List.of(summary.category()),
                new MerchantSectionDto(List.of(), false),
                new MerchantSectionDto(List.of(summary), false),
                new MerchantSectionDto(List.of(), false));
        MerchantSearchResponse search = new MerchantSearchResponse(
                List.of(summary.category()), List.of(summary), 0, 20, 1, false);

        JsonNode homeJson = objectMapper.readTree(objectMapper.writeValueAsBytes(home));
        JsonNode searchJson = objectMapper.readTree(objectMapper.writeValueAsBytes(search));

        assertEquals(Set.of("categories", "nearby", "recommended", "featured"), keys(homeJson));
        assertEquals(
                Set.of("categories", "merchants", "page", "pageSize", "totalCount", "hasMore"),
                keys(searchJson));
        assertEquals("Café Órbita", searchJson.at("/merchants/0/name").textValue());
        assertTrue(searchJson.get("page").isIntegralNumber());
        assertTrue(searchJson.get("hasMore").isBoolean());
        assertEquals("UNKNOWN", searchJson.at("/merchants/0/availability/status").textValue());
        assertTrue(searchJson.at("/merchants/0/catalogAvailable").booleanValue());
        assertFalse(searchJson.at("/merchants/0").has("location"));
        assertFalse(homeJson.has("capabilities"));
    }

    @Test
    void optionalNullsAreOmittedAndEmptyListsRemainArrays() throws Exception {
        MerchantOverviewResponse detail = new MerchantOverviewResponse(
                "cafe-orbita",
                "Café Órbita",
                null,
                null,
                new MerchantCategoryDto("cafe", "Cafés"),
                null,
                null,
                new MerchantAvailabilityDto("UNKNOWN", null, null),
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                false);
        MerchantSearchResponse empty =
                new MerchantSearchResponse(List.of(), List.of(), 7, 20, 0, false);

        JsonNode detailJson = objectMapper.readTree(objectMapper.writeValueAsBytes(detail));
        JsonNode emptyJson = objectMapper.readTree(objectMapper.writeValueAsBytes(empty));

        assertFalse(detailJson.has("shortDescription"));
        assertFalse(detailJson.has("rating"));
        assertFalse(detailJson.has("contact"));
        assertFalse(detailJson.has("schedule"));
        assertTrue(detailJson.get("fulfillmentOptions").isArray());
        assertTrue(emptyJson.get("categories").isArray());
        assertTrue(emptyJson.get("merchants").isArray());
        assertEquals(0, emptyJson.get("totalCount").intValue());
        assertFalse(detailJson.has("capabilities"));
    }

    @Test
    void enumLikeTransportValuesAreStringsAndCanRepresentFutureValues() throws Exception {
        MerchantAvailabilityDto futureAvailability =
                new MerchantAvailabilityDto("FUTURE_STATUS", null, null);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(futureAvailability));

        assertTrue(json.get("status").isTextual());
        assertEquals("FUTURE_STATUS", json.get("status").asText());
    }

    @Test
    void standardizedErrorUsesIsoInstantAndNeverSerializesInternalDetails() throws Exception {
        Instant timestamp = Instant.parse("2026-07-20T12:34:56Z");
        DiscoveryErrorResponse error = new DiscoveryErrorResponse(
                timestamp,
                503,
                "Service Unavailable",
                "SERVICE_UNAVAILABLE",
                "O serviço Discovery está temporariamente indisponível.",
                "/api/v1/discovery/home");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(error));

        assertEquals(timestamp.toString(), json.get("timestamp").textValue());
        assertEquals(
                Set.of("timestamp", "status", "error", "code", "message", "path"), keys(json));
        assertFalse(json.has("exception"));
        assertFalse(json.has("stackTrace"));
        assertFalse(json.has("sql"));
    }

    @Test
    void publicResponseRecordsContainNoForbiddenAdministrativeFieldNames() {
        Set<Class<?>> visited = new HashSet<>();
        for (Class<?> type : List.of(
                DiscoveryHomeResponse.class,
                MerchantSearchResponse.class,
                MerchantOverviewResponse.class,
                DiscoveryErrorResponse.class,
                MerchantSummaryDto.class,
                MerchantCategoryDto.class,
                MerchantAvailabilityDto.class,
                MerchantAddressDto.class,
                MerchantContactDto.class,
                MerchantLocationDto.class,
                MerchantPromotionDto.class,
                MerchantRatingDto.class,
                MoneyAmountDto.class,
                WeeklyScheduleDto.class)) {
            assertNoPrivateField(type, visited);
        }
    }

    private MerchantSummaryDto summary() {
        return new MerchantSummaryDto(
                "cafe-orbita",
                "Café Órbita",
                new MerchantCategoryDto("cafe", "Cafés"),
                null,
                null,
                new MerchantAvailabilityDto("UNKNOWN", null, null),
                Set.of("DINE_IN"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true);
    }

    private Set<String> keys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private void assertNoPrivateField(Class<?> type, Set<Class<?>> visited) {
        if (!type.isRecord() || !visited.add(type)) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            String field = component.getName().toLowerCase(Locale.ROOT);
            assertFalse(PRIVATE_FIELDS.contains(field), type.getName() + " exposes " + field);
            Class<?> componentType = component.getType();
            if (componentType.isRecord()) {
                assertNoPrivateField(componentType, visited);
            }
        }
    }
}
