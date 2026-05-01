package com.restaurante.store.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAnalyticsEventRepository extends JpaRepository<StoreAnalyticsEvent, Long> {
}
