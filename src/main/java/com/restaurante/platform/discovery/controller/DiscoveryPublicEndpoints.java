package com.restaurante.platform.discovery.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;

/** Canonical allowlist for the three public Discovery GET routes. */
public final class DiscoveryPublicEndpoints {

    public static final String[] SECURITY_PATTERNS = {
        "/api/v1/discovery/home",
        "/api/v1/discovery/search",
        "/api/v1/discovery/merchant/*",
        "/v1/discovery/home",
        "/v1/discovery/search",
        "/v1/discovery/merchant/*"
    };

    private static final String[] PUBLIC_PREFIXES = {"/api/v1/discovery", "/v1/discovery"};

    private DiscoveryPublicEndpoints() {}

    public static boolean matches(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        for (String prefix : PUBLIC_PREFIXES) {
            if ((prefix + "/home").equals(path) || (prefix + "/search").equals(path)) {
                return true;
            }
            String merchantPrefix = prefix + "/merchant/";
            if (path.startsWith(merchantPrefix)) {
                String merchantId = path.substring(merchantPrefix.length());
                return !merchantId.isEmpty() && !merchantId.contains("/");
            }
        }
        return false;
    }
}
