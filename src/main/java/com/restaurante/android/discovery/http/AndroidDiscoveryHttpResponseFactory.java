package com.restaurante.android.discovery.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public final class AndroidDiscoveryHttpResponseFactory {

    static final String CACHE_CONTROL = "public, max-age=60";
    private final ObjectMapper objectMapper;

    public AndroidDiscoveryHttpResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> ResponseEntity<T> cacheable(
            T body, String ifNoneMatch, DiscoveryRateLimitDecision rateLimit) {
        String etag = etag(body);
        HttpHeaders headers = headers(etag, rateLimit);
        if (matches(ifNoneMatch, etag)) {
            return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    String etag(Object representation) {
        try {
            byte[] json = objectMapper.writeValueAsString(representation).getBytes(StandardCharsets.UTF_8);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not generate deterministic Discovery ETag.", exception);
        }
    }

    private HttpHeaders headers(String etag, DiscoveryRateLimitDecision rateLimit) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        headers.setETag(etag);
        headers.set("X-RateLimit-Limit", Integer.toString(rateLimit.limit()));
        headers.set("X-RateLimit-Remaining", Integer.toString(rateLimit.remaining()));
        return headers;
    }

    private boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized) || etag.equals(normalized)
                    || (normalized.startsWith("W/") && etag.equals(normalized.substring(2)))) {
                return true;
            }
        }
        return false;
    }
}
