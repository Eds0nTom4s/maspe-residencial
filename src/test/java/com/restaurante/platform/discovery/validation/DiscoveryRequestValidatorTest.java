package com.restaurante.platform.discovery.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.exception.DiscoveryValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscoveryRequestValidatorTest {

    private DiscoveryRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DiscoveryRequestValidator();
    }

    @Test
    void appliesDefaultsAndTrimsQueryAndIds() {
        SearchDiscoveryRequest request = validator.validate(new SearchDiscoveryRequest(
                "  Café  ", " bakery ", null, null, "  ", null, null, " name "));

        assertEquals("Café", request.query());
        assertEquals("bakery", request.categoryId());
        assertNull(request.municipalityId());
        assertEquals(0, request.page());
        assertEquals(20, request.pageSize());
        assertEquals("NAME", request.sort());
    }

    @Test
    void acceptsGlobalCoordinateLimitsAndNameSort() {
        HomeDiscoveryRequest request = validator.validate(
                new HomeDiscoveryRequest(-90D, 180D, null, null, 0, 100, "name"));

        assertEquals(-90D, request.latitude());
        assertEquals(180D, request.longitude());
        assertEquals("NAME", request.sort());
    }

    @Test
    void acceptsZeroCoordinatesAndCoordinatesOutsideAngola() {
        HomeDiscoveryRequest zero = validator.validate(
                new HomeDiscoveryRequest(0D, 0D, null, null, 0, 20, null));
        HomeDiscoveryRequest global = validator.validate(
                new HomeDiscoveryRequest(48.8566, 2.3522, null, null, 0, 20, null));

        assertEquals(0D, zero.latitude());
        assertEquals("NAME", zero.sort());
        assertEquals(48.8566, global.latitude());
    }

    @Test
    void rejectsNegativePageAndInvalidPageSize() {
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(
                        new HomeDiscoveryRequest(null, null, null, null, -1, 20, null)));
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new SearchDiscoveryRequest(
                        null, null, null, null, null, 0, 0, null)));
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new SearchDiscoveryRequest(
                        null, null, null, null, null, 0, 101, null)));
    }

    @Test
    void distinguishesUnknownAndKnownButUnsupportedSorts() {
        DiscoveryValidationException invalid = assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new SearchDiscoveryRequest(
                        "query", null, null, null, null, 0, 20, "distance")));
        assertEquals(DiscoveryError.INVALID_REQUEST, invalid.getReason());

        for (String sort : new String[] {"NEAREST", "TOP_RATED", "MOST_POPULAR", "FEATURED"}) {
            DiscoveryValidationException unsupported = assertThrows(
                    DiscoveryValidationException.class,
                    () -> validator.validate(new SearchDiscoveryRequest(
                            "query", null, null, null, null, 0, 20, sort)));
            assertEquals(DiscoveryError.SORT_NOT_SUPPORTED, unsupported.getReason());
        }
    }

    @Test
    void rejectsOversizedQuery() {
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new SearchDiscoveryRequest(
                        "x".repeat(101), null, null, null, null, 0, 20, null)));
    }

    @Test
    void queryAllowsEmptyOneCharacterAndOneHundredAfterTrim() {
        assertEquals(
                "",
                validator.validate(new SearchDiscoveryRequest(
                                "   ", null, null, null, null, null, null, null))
                        .query());
        assertEquals(
                "a",
                validator.validate(new SearchDiscoveryRequest(
                                " a ", null, null, null, null, null, null, null))
                        .query());
        assertEquals(
                100,
                validator.validate(new SearchDiscoveryRequest(
                                " " + "x".repeat(100) + " ",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .query()
                        .length());
    }

    @Test
    void rejectsIncompleteNonFiniteAndOutOfRangeCoordinates() {
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new HomeDiscoveryRequest(
                        -8.8, null, null, null, 0, 20, null)));
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new HomeDiscoveryRequest(
                        Double.NaN, 13.2, null, null, 0, 20, null)));
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new HomeDiscoveryRequest(
                        91D, 13.2, null, null, 0, 20, null)));
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new HomeDiscoveryRequest(
                        -8.8, -181D, null, null, 0, 20, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UPPERCASE", "-merchant", "merchant_1", "merchant-"})
    void rejectsInvalidMerchantIds(String merchantId) {
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new MerchantRequest(merchantId)));
    }

    @Test
    void trimsValidMerchantId() {
        assertEquals(
                "sabor-maianga",
                validator.validate(new MerchantRequest(" sabor-maianga ")).merchantId());
    }

    @Test
    void validatesPublicFilterIdsWithoutRejectingUnicodeMunicipality() {
        SearchDiscoveryRequest request = validator.validate(new SearchDiscoveryRequest(
                "  Café 🍰 ' % _  ",
                "restaurant",
                null,
                null,
                "  São João do Prenda  ",
                null,
                null,
                null));

        assertEquals("Café 🍰 ' % _", request.query());
        assertEquals("São João do Prenda", request.municipalityId());
        assertEquals("NAME", request.sort());

        for (String categoryId : new String[] {"UPPER", "restaurant_1", "%"}) {
            assertThrows(
                    DiscoveryValidationException.class,
                    () -> validator.validate(new SearchDiscoveryRequest(
                            "", categoryId, null, null, null, 0, 20, "NAME")));
        }
        assertThrows(
                DiscoveryValidationException.class,
                () -> validator.validate(new SearchDiscoveryRequest(
                        "", null, null, null, "Luanda%", 0, 20, "NAME")));
    }
}
