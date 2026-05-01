package com.restaurante.store.service;

import com.restaurante.store.analytics.StoreAnalyticsEvent;
import com.restaurante.store.analytics.StoreAnalyticsEventRepository;
import org.springframework.stereotype.Service;

@Service
public class StoreAnalyticsService {

    private final StoreAnalyticsEventRepository repository;

    public StoreAnalyticsService(StoreAnalyticsEventRepository repository) {
        this.repository = repository;
    }

    public void track(String eventType, String socioId, Long productId, Long orderId, String metadata) {
        repository.save(new StoreAnalyticsEvent(eventType, socioId, productId, orderId, metadata));
    }
}
