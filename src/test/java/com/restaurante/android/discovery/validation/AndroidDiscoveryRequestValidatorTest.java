package com.restaurante.android.discovery.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurante.android.api.error.AndroidPublicApiException;
import com.restaurante.android.api.error.AndroidPublicErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AndroidDiscoveryRequestValidatorTest {

    private final AndroidDiscoveryRequestValidator validator = new AndroidDiscoveryRequestValidator();

    @Test
    void usesZeroBasedDefaultsAndAcceptsOnlyRealNameSort() {
        MockHttpServletRequest request = request("/api/v1/discovery/search");
        request.addParameter("query", "  Café  ");
        request.addParameter("sort", "name");

        ValidatedDiscoveryQuery result = validator.search(request);

        assertThat(result.query()).isEqualTo("Café");
        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.sort()).isEqualTo("NAME");
    }

    @Test
    void rejectsUnknownRepeatedAndOutOfBoundsParameters() {
        assertInvalid(requestWith("unknown", "x"), "UNKNOWN_PARAMETER");

        MockHttpServletRequest repeated = request("/api/v1/discovery/home");
        repeated.addParameter("page", "0", "1");
        assertInvalid(repeated, "REPEATED_PARAMETER");

        assertInvalid(requestWith("pageSize", "101"), "OUT_OF_RANGE");
    }

    @Test
    void coordinatesAreFiniteBoundedAndRequiredAsPair() {
        assertInvalid(requestWith("latitude", "-8.9"), "COORDINATE_PAIR_REQUIRED");
        MockHttpServletRequest outOfRange = request("/api/v1/discovery/home");
        outOfRange.addParameter("latitude", "91");
        outOfRange.addParameter("longitude", "13");
        assertInvalid(outOfRange, "OUT_OF_RANGE");
        MockHttpServletRequest nonFinite = request("/api/v1/discovery/home");
        nonFinite.addParameter("latitude", "NaN");
        nonFinite.addParameter("longitude", "13");
        assertInvalid(nonFinite, "OUT_OF_RANGE");
    }

    @Test
    void rejectsCapabilitiesWithoutCanonicalPersistence() {
        assertCode(searchWith("sort", "NEAREST"), AndroidPublicErrorCode.SORT_NOT_SUPPORTED);
        assertInvalid(searchWith("onlyOpen", "true"), "CAPABILITY_NOT_SUPPORTED");
        assertInvalid(searchWith("fulfillmentOptions", "PICKUP"), "CAPABILITY_NOT_SUPPORTED");
        assertInvalid(searchWith("categoryId", "9f4b774a-5f11-4e8e-9a1f-30b53eb6db68"),
                "CAPABILITY_NOT_SUPPORTED");
    }

    private MockHttpServletRequest requestWith(String name, String value) {
        MockHttpServletRequest request = request("/api/v1/discovery/home");
        request.addParameter(name, value);
        return request;
    }

    private MockHttpServletRequest searchWith(String name, String value) {
        MockHttpServletRequest request = request("/api/v1/discovery/search");
        request.addParameter(name, value);
        return request;
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private void assertInvalid(MockHttpServletRequest request, String fieldCode) {
        assertThatThrownBy(() -> {
                    if (uriIsSearch(request)) {
                        validator.search(request);
                    } else {
                        validator.home(request);
                    }
                })
                .isInstanceOf(AndroidPublicApiException.class)
                .satisfies(error -> assertThat(((AndroidPublicApiException) error)
                        .getFieldErrors().getFirst().code()).isEqualTo(fieldCode));
    }

    private void assertCode(MockHttpServletRequest request, AndroidPublicErrorCode code) {
        assertThatThrownBy(() -> validator.search(request))
                .isInstanceOf(AndroidPublicApiException.class)
                .satisfies(error -> assertThat(((AndroidPublicApiException) error).getCode()).isEqualTo(code));
    }

    private boolean uriIsSearch(MockHttpServletRequest request) {
        return request.getRequestURI().endsWith("/search");
    }
}
