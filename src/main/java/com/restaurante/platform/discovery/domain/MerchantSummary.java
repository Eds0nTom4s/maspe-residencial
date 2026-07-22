package com.restaurante.platform.discovery.domain;

import java.util.Set;

public record MerchantSummary(
        String id,
        String name,
        MerchantCategory category,
        String shortDescription,
        String imageUrl,
        MerchantAvailability availability,
        Set<FulfillmentOption> fulfillmentOptions,
        MerchantLocation location,
        Integer estimatedPreparationMinutes,
        MerchantRating rating,
        Integer popularity,
        MoneyAmount minimumOrderAmount,
        MerchantPromotion promotion,
        Boolean featured,
        boolean catalogAvailable) {

    public MerchantSummary {
        fulfillmentOptions = Set.copyOf(fulfillmentOptions);
    }
}
