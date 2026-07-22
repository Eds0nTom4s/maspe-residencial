package com.restaurante.platform.discovery.domain;

public record MerchantLocation(
        Double latitude, Double longitude, String municipalityId, Integer distanceMeters) {}
