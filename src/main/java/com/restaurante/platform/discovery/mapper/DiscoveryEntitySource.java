package com.restaurante.platform.discovery.mapper;

import java.time.LocalTime;
import java.util.Set;

/** Minimal entity-side contract accepted by the entity-to-domain mapper. */
public final class DiscoveryEntitySource {

    private DiscoveryEntitySource() {}

    public interface Category {
        String id();

        String name();
    }

    public interface Merchant {
        String id();

        String name();

        String categoryId();

        String shortDescription();

        String imageUrl();

        String availability();

        Integer minutesRemaining();

        LocalTime opensAt();

        Set<String> fulfillmentOptions();

        int distanceMeters();

        Integer estimatedPreparationMinutes();

        Double rating();

        Integer ratingCount();

        int popularity();

        Long minimumOrderMinorUnits();

        String promotionId();

        String promotionTitle();

        String promotionDescription();

        String promotionBadge();

        boolean featured();
    }
}
