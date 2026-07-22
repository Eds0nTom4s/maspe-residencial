package com.restaurante.platform.discovery.dto;

public record HomeDiscoveryRequest(
        Double latitude,
        Double longitude,
        String municipalityId,
        String categoryId,
        Integer page,
        Integer pageSize,
        String sort) {}
