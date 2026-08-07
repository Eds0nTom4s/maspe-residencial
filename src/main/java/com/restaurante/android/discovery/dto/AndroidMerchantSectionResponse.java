package com.restaurante.android.discovery.dto;

import java.util.List;

public record AndroidMerchantSectionResponse(
        List<AndroidMerchantSummaryResponse> items,
        boolean hasMore) {

    public AndroidMerchantSectionResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
