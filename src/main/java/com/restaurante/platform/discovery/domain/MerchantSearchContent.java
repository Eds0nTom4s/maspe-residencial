package com.restaurante.platform.discovery.domain;

import java.util.List;

public record MerchantSearchContent(
        List<MerchantCategory> categories,
        List<MerchantSummary> merchants,
        int page,
        int pageSize,
        int totalCount,
        boolean hasMore) {

    public MerchantSearchContent {
        categories = List.copyOf(categories);
        merchants = List.copyOf(merchants);
    }
}
