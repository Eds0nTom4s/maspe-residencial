package com.restaurante.platform.discovery.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurante.security.CustomUserDetailsService;
import com.restaurante.security.JwtAuthenticationFactory;
import com.restaurante.security.JwtAuthenticationFilter;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.JwtUserStatusValidator;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextFilter;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.security.tenant.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DiscoveryPublicFilterTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void onlyTheThreeExactGetEndpointsSkipJwtAuthentication() {
        TestableJwtFilter jwtFilter = jwtFilter();

        for (String path : new String[] {
            "/api/v1/discovery/home",
            "/api/v1/discovery/search",
            "/api/v1/discovery/merchant/sabor-maianga",
            "/v1/discovery/home",
            "/v1/discovery/search",
            "/v1/discovery/merchant/sabor-maianga"
        }) {
            assertTrue(jwtFilter.skips(new MockHttpServletRequest("GET", path)));
        }

        for (MockHttpServletRequest request : new MockHttpServletRequest[] {
            new MockHttpServletRequest("POST", "/api/v1/discovery/search"),
            new MockHttpServletRequest("PUT", "/api/v1/discovery/home"),
            new MockHttpServletRequest("PATCH", "/api/v1/discovery/merchant/sabor-maianga"),
            new MockHttpServletRequest("DELETE", "/api/v1/discovery/merchant/sabor-maianga"),
            new MockHttpServletRequest("GET", "/api/v1/discovery/merchant/a/extra"),
            new MockHttpServletRequest("GET", "/api/v1/private")
        }) {
            assertFalse(jwtFilter.skips(request));
        }
    }

    @Test
    void malformedJwtAndArbitraryTenantHeadersCannotAffectPublicRequest() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        TestableJwtFilter jwtFilter = new TestableJwtFilter(
                tokenProvider,
                mock(CustomUserDetailsService.class),
                mock(JwtAuthenticationFactory.class),
                mock(JwtUserStatusValidator.class));
        TenantResolver resolver = mock(TenantResolver.class);
        TestableTenantFilter tenantFilter = new TestableTenantFilter(resolver);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/discovery/search");
        request.addHeader("Authorization", "Bearer definitely.invalid");
        request.addHeader("X-Tenant-Id", "41");
        request.addHeader("X-Tenant-Id", "99");
        AtomicBoolean reachedController = new AtomicBoolean();
        FilterChain terminal = (ignoredRequest, ignoredResponse) -> {
            reachedController.set(true);
            assertTrue(TenantContextHolder.get().isEmpty());
        };

        jwtFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                (jwtRequest, jwtResponse) -> tenantFilter.doFilter(jwtRequest, jwtResponse, terminal));

        assertTrue(reachedController.get());
        assertTrue(TenantContextHolder.get().isEmpty());
        verifyNoInteractions(tokenProvider, resolver);
    }

    @Test
    void privateThenPublicThenPrivateWithoutTenantNeverLeaksContext() throws Exception {
        TenantResolver resolver = mock(TenantResolver.class);
        TenantContext tenantA = context(1L);
        when(resolver.resolve(any(HttpServletRequest.class)))
                .thenReturn(Optional.of(tenantA), Optional.empty());
        TestableTenantFilter filter = new TestableTenantFilter(resolver);

        filter.doFilter(
                new MockHttpServletRequest("GET", "/tenant/private"),
                new MockHttpServletResponse(),
                (request, response) -> assertTrue(TenantContextHolder.get().isPresent()));
        assertTrue(TenantContextHolder.get().isEmpty());

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/discovery/home"),
                new MockHttpServletResponse(),
                (request, response) -> assertTrue(TenantContextHolder.get().isEmpty()));
        assertTrue(TenantContextHolder.get().isEmpty());

        filter.doFilter(
                new MockHttpServletRequest("GET", "/tenant/private"),
                new MockHttpServletResponse(),
                (request, response) -> assertTrue(TenantContextHolder.get().isEmpty()));
        assertTrue(TenantContextHolder.get().isEmpty());
    }

    @Test
    void publicFailureStillClearsResidualTenantContext() {
        TestableTenantFilter filter = new TestableTenantFilter(mock(TenantResolver.class));
        TenantContextHolder.set(context(7L));

        try {
            filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/v1/discovery/home"),
                    new MockHttpServletResponse(),
                    (request, response) -> {
                        assertTrue(TenantContextHolder.get().isEmpty());
                        throw new IllegalStateException("controlled");
                    });
        } catch (Exception expected) {
            assertTrue(TenantContextHolder.get().isEmpty());
        }
    }

    @Test
    void concurrentRequestsKeepTenantContextsThreadLocal() throws Exception {
        TenantResolver resolver = mock(TenantResolver.class);
        when(resolver.resolve(any(HttpServletRequest.class))).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            return Optional.of(context(Long.parseLong(request.getHeader("test-tenant"))));
        });
        TestableTenantFilter filter = new TestableTenantFilter(resolver);
        CountDownLatch bothInside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean isolated = new AtomicBoolean(true);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runConcurrent(filter, 11L, bothInside, release, isolated));
            var second = executor.submit(() -> runConcurrent(filter, 22L, bothInside, release, isolated));
            assertTrue(bothInside.await(5, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertTrue(isolated.get());
        assertTrue(TenantContextHolder.get().isEmpty());
    }

    private void runConcurrent(
            TestableTenantFilter filter,
            long tenantId,
            CountDownLatch bothInside,
            CountDownLatch release,
            AtomicBoolean isolated) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant/private");
        request.addHeader("test-tenant", Long.toString(tenantId));
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (ignored, response) -> {
                isolated.compareAndSet(
                        true,
                        TenantContextHolder.get().map(TenantContext::tenantId).orElse(-1L)
                                == tenantId);
                bothInside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    isolated.set(false);
                }
                isolated.compareAndSet(
                        true,
                        TenantContextHolder.get().map(TenantContext::tenantId).orElse(-1L)
                                == tenantId);
            });
            isolated.compareAndSet(true, TenantContextHolder.get().isEmpty());
        } catch (Exception exception) {
            isolated.set(false);
        }
    }

    private TestableJwtFilter jwtFilter() {
        return new TestableJwtFilter(
                mock(JwtTokenProvider.class),
                mock(CustomUserDetailsService.class),
                mock(JwtAuthenticationFactory.class),
                mock(JwtUserStatusValidator.class));
    }

    private TenantContext context(long tenantId) {
        return new TenantContext(
                tenantId,
                "T-" + tenantId,
                100L,
                Set.of("TENANT_ADMIN"),
                TenantResolutionSource.JWT,
                false,
                false);
    }

    private static final class TestableJwtFilter extends JwtAuthenticationFilter {

        private TestableJwtFilter(
                JwtTokenProvider tokenProvider,
                CustomUserDetailsService userDetailsService,
                JwtAuthenticationFactory authenticationFactory,
                JwtUserStatusValidator userStatusValidator) {
            super(tokenProvider, userDetailsService, authenticationFactory, userStatusValidator);
        }

        private boolean skips(HttpServletRequest request) {
            return shouldNotFilter(request);
        }
    }

    private static final class TestableTenantFilter extends TenantContextFilter {

        private TestableTenantFilter(TenantResolver tenantResolver) {
            super(tenantResolver);
        }
    }
}
