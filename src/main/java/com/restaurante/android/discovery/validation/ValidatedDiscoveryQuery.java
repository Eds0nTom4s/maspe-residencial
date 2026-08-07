package com.restaurante.android.discovery.validation;

public record ValidatedDiscoveryQuery(
        String query,
        String municipality,
        Double latitude,
        Double longitude,
        int page,
        int pageSize,
        String sort) {
}
