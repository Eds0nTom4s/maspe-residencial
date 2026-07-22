package com.restaurante.platform.discovery.repository;

import com.restaurante.platform.discovery.mapper.DiscoveryEntitySource;

record InMemoryCategoryEntity(String id, String name) implements DiscoveryEntitySource.Category {}
