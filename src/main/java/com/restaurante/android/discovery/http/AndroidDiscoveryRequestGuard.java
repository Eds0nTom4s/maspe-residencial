package com.restaurante.android.discovery.http;

import com.restaurante.android.api.error.AndroidPublicApiException;
import com.restaurante.android.api.error.AndroidPublicErrorCode;
import com.restaurante.android.api.error.AndroidPublicFieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class AndroidDiscoveryRequestGuard {

    private static final List<String> TENANT_OVERRIDE_HEADERS =
            List.of("X-Tenant-Id", "X-Tenant-Code", "X-Business-Id");

    private final AndroidDiscoveryRateLimiter rateLimiter;

    public AndroidDiscoveryRequestGuard(AndroidDiscoveryRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public DiscoveryRateLimitDecision check(HttpServletRequest request, String operation) {
        for (String header : TENANT_OVERRIDE_HEADERS) {
            if (request.getHeader(header) != null) {
                throw new AndroidPublicApiException(
                        AndroidPublicErrorCode.INVALID_REQUEST,
                        HttpStatus.BAD_REQUEST,
                        "Tenant override não é permitido.",
                        false,
                        List.of(new AndroidPublicFieldError(
                                "/headers/" + header,
                                "TENANT_OVERRIDE_FORBIDDEN",
                                "O tenant é resolvido exclusivamente no servidor.")));
            }
        }
        String remoteAddress = request.getRemoteAddr();
        String safeAddress = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        DiscoveryRateLimitDecision decision = rateLimiter.acquire(operation + "|" + safeAddress);
        if (!decision.allowed()) {
            throw new AndroidPublicApiException(
                    AndroidPublicErrorCode.RATE_LIMITED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de pedidos excedido.",
                    true,
                    List.of(),
                    decision.retryAfterSeconds());
        }
        return decision;
    }
}
