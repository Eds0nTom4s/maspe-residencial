package com.restaurante.android.discovery.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;

/** Exact allowlist shared by security filters for the three public Discovery GET routes. */
public final class AndroidDiscoveryPublicPaths {

    private static final String[] PREFIXES = {"/api/v1/discovery", "/v1/discovery"};

    private AndroidDiscoveryPublicPaths() {
    }

    public static boolean matches(HttpServletRequest request) {
        if (request == null || !HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        for (String prefix : PREFIXES) {
            if ((prefix + "/home").equals(path) || (prefix + "/search").equals(path)) {
                return true;
            }
            String detailPrefix = prefix + "/merchants/";
            if (path.startsWith(detailPrefix)) {
                String merchantId = path.substring(detailPrefix.length());
                return !merchantId.isEmpty() && !merchantId.contains("/");
            }
        }
        return false;
    }
}
