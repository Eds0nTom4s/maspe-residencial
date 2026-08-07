package com.restaurante.android.discovery.http;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded per-instance fixed-window limit; no client-provided IP header is trusted. */
@Component
public final class AndroidDiscoveryRateLimiter {

    private final int requestsPerMinute;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);

    @Autowired
    public AndroidDiscoveryRateLimiter(
            @Value("${consuma.android.discovery.rate-limit-per-minute:180}") int requestsPerMinute,
            @Value("${consuma.android.discovery.rate-limit-max-keys:10000}") int maxTrackedKeys) {
        this(requestsPerMinute, maxTrackedKeys, Clock.systemUTC());
    }

    AndroidDiscoveryRateLimiter(int requestsPerMinute, int maxTrackedKeys, Clock clock) {
        if (requestsPerMinute < 1 || maxTrackedKeys < 1) {
            throw new IllegalArgumentException("Discovery rate-limit configuration must be positive.");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    public synchronized DiscoveryRateLimitDecision acquire(String key) {
        long epochSecond = Instant.now(clock).getEpochSecond();
        long minute = Math.floorDiv(epochSecond, 60);
        long retryAfter = 60 - Math.floorMod(epochSecond, 60);
        Window current = windows.get(key);
        if (current == null || current.minute() != minute) {
            trimExpired(minute);
            current = new Window(minute, 0);
        }
        if (current.count() >= requestsPerMinute) {
            windows.put(key, current);
            return new DiscoveryRateLimitDecision(requestsPerMinute, 0, retryAfter, false);
        }
        Window updated = new Window(minute, current.count() + 1);
        windows.put(key, updated);
        trimSize();
        return new DiscoveryRateLimitDecision(
                requestsPerMinute, requestsPerMinute - updated.count(), retryAfter, true);
    }

    private void trimExpired(long minute) {
        windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
    }

    private void trimSize() {
        Iterator<String> keys = windows.keySet().iterator();
        while (windows.size() > maxTrackedKeys && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private record Window(long minute, int count) {
    }
}
