package com.restaurante.platform.discovery.dto;

public record SearchDiscoveryRequest(
        String query,
        String categoryId,
        Double latitude,
        Double longitude,
        String municipalityId,
        Integer page,
        Integer pageSize,
        String sort) {}
