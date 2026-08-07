package com.restaurante.android.discovery.http;

public record DiscoveryRateLimitDecision(
        int limit, int remaining, long retryAfterSeconds, boolean allowed) {
}
