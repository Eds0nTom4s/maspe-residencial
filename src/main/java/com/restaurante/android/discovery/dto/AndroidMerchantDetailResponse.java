package com.restaurante.android.discovery.dto;

import java.util.List;
import java.util.UUID;

public record AndroidMerchantDetailResponse(
        UUID merchantId,
        String name,
        AndroidMerchantAvailability availability,
        List<String> fulfillmentOptions,
        Integer distanceMeters,
        Object rating,
        Double popularityScore,
        boolean featured,
        boolean catalogAvailable,
        String fullDescription,
        List<Object> weeklySchedule,
        UUID catalogId) {

    public AndroidMerchantDetailResponse {
        fulfillmentOptions = fulfillmentOptions == null ? List.of() : List.copyOf(fulfillmentOptions);
        weeklySchedule = weeklySchedule == null ? null : List.copyOf(weeklySchedule);
    }
}
