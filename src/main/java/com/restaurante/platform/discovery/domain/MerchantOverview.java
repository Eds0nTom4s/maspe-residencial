package com.restaurante.platform.discovery.domain;

import java.util.Set;

public record MerchantOverview(
        String id,
        String name,
        String shortDescription,
        String fullDescription,
        MerchantCategory category,
        String bannerUrl,
        String logoUrl,
        MerchantAvailability availability,
        Set<FulfillmentOption> fulfillmentOptions,
        MerchantRating rating,
        MerchantAddress address,
        MerchantContact contact,
        WeeklySchedule schedule,
        MerchantPromotion promotion,
        boolean catalogAvailable) {

    public MerchantOverview {
        fulfillmentOptions = Set.copyOf(fulfillmentOptions);
    }
}
