package com.restaurante.android.discovery.dto;

import java.util.List;
import java.util.UUID;

public record AndroidMerchantSummaryResponse(
        UUID merchantId,
        String name,
        AndroidMerchantAvailability availability,
        List<String> fulfillmentOptions,
        Integer distanceMeters,
        Object rating,
        Double popularityScore,
        boolean featured,
        boolean catalogAvailable) {

    public AndroidMerchantSummaryResponse {
        fulfillmentOptions = fulfillmentOptions == null ? List.of() : List.copyOf(fulfillmentOptions);
    }
}
