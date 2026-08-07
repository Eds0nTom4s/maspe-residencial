package com.restaurante.android.discovery.dto;

import java.util.List;

public record AndroidDiscoverySearchResponse(
        List<Object> categories,
        List<AndroidMerchantSummaryResponse> merchants,
        int page,
        int pageSize,
        long totalCount,
        boolean hasMore) {

    public AndroidDiscoverySearchResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
        merchants = merchants == null ? List.of() : List.copyOf(merchants);
    }
}
