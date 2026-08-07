package com.restaurante.android.discovery.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.android.api.error.AndroidPublicApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class AndroidDiscoveryHttpFoundationTest {

    @Test
    void permitsExactlyTheConfiguredWindowThenRejects() {
        AndroidDiscoveryRateLimiter limiter = new AndroidDiscoveryRateLimiter(
                2, 10, Clock.fixed(Instant.parse("2026-08-07T10:00:10Z"), ZoneOffset.UTC));

        assertThat(limiter.acquire("ip").allowed()).isTrue();
        DiscoveryRateLimitDecision lastAllowed = limiter.acquire("ip");
        assertThat(lastAllowed.allowed()).isTrue();
        assertThat(lastAllowed.remaining()).isZero();
        assertThat(limiter.acquire("ip").allowed()).isFalse();
    }

    @Test
    void guardRejectsTenantOverrideWithoutTrustingForwardedIp() {
        AndroidDiscoveryRequestGuard guard = new AndroidDiscoveryRequestGuard(
                new AndroidDiscoveryRateLimiter(180, 100, Clock.systemUTC()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/discovery/home");
        request.addHeader("X-Tenant-Id", "42");
        request.addHeader("X-Forwarded-For", "203.0.113.4");

        assertThatThrownBy(() -> guard.check(request, "home"))
                .isInstanceOf(AndroidPublicApiException.class)
                .satisfies(error -> assertThat(((AndroidPublicApiException) error).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void guardReturnsRetryable429OnlyAfterTheConfiguredRequestCount() {
        AndroidDiscoveryRequestGuard guard = new AndroidDiscoveryRequestGuard(
                new AndroidDiscoveryRateLimiter(
                        1, 10, Clock.fixed(Instant.parse("2026-08-07T10:00:10Z"), ZoneOffset.UTC)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/discovery/home");
        request.setRemoteAddr("192.0.2.10");

        assertThat(guard.check(request, "home").remaining()).isZero();
        assertThatThrownBy(() -> guard.check(request, "home"))
                .isInstanceOf(AndroidPublicApiException.class)
                .satisfies(error -> {
                    AndroidPublicApiException publicError = (AndroidPublicApiException) error;
                    assertThat(publicError.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(publicError.isRetryable()).isTrue();
                    assertThat(publicError.getRetryAfterSeconds()).isEqualTo(50L);
                });
    }

    @Test
    void etagIsDeterministicAndSupportsBodyless304() {
        AndroidDiscoveryHttpResponseFactory factory =
                new AndroidDiscoveryHttpResponseFactory(new ObjectMapper());
        DiscoveryRateLimitDecision rate = new DiscoveryRateLimitDecision(180, 179, 50, true);

        var first = factory.cacheable(Map.of("value", "stable"), null, rate);
        var repeat = factory.cacheable(Map.of("value", "stable"), first.getHeaders().getETag(), rate);
        var changed = factory.cacheable(Map.of("value", "changed"), null, rate);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("public, max-age=60");
        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(repeat.getBody()).isNull();
        assertThat(changed.getHeaders().getETag()).isNotEqualTo(first.getHeaders().getETag());
    }
}
