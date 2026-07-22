package com.restaurante.platform.discovery.repository;

import com.restaurante.platform.discovery.mapper.DiscoveryEntitySource;
import java.time.LocalTime;
import java.util.Set;

record InMemoryMerchantEntity(
        String id,
        String name,
        String categoryId,
        String shortDescription,
        String imageUrl,
        String availability,
        Integer minutesRemaining,
        LocalTime opensAt,
        Set<String> fulfillmentOptions,
        int distanceMeters,
        Integer estimatedPreparationMinutes,
        Double rating,
        Integer ratingCount,
        int popularity,
        Long minimumOrderMinorUnits,
        String promotionId,
        String promotionTitle,
        String promotionDescription,
        String promotionBadge,
        boolean featured)
        implements DiscoveryEntitySource.Merchant {

    InMemoryMerchantEntity {
        fulfillmentOptions = Set.copyOf(fulfillmentOptions);
    }
}
