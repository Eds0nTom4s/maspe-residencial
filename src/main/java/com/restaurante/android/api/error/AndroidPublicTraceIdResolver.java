package com.restaurante.android.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

public class AndroidPublicTraceIdResolver {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");

    public String resolve(HttpServletRequest request) {
        String candidate = firstSafe(request, "X-Request-Id");
        if (candidate == null) {
            candidate = firstSafe(request, "X-Correlation-Id");
        }
        return candidate != null ? candidate : UUID.randomUUID().toString();
    }

    private String firstSafe(HttpServletRequest request, String header) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(header);
        return value != null && SAFE_TRACE_ID.matcher(value).matches() ? value : null;
    }
}
