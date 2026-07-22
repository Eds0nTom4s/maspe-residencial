package com.restaurante.platform.discovery.domain;

import java.util.List;

public record MerchantSection(List<MerchantSummary> items, boolean hasMore) {

    public MerchantSection {
        items = List.copyOf(items);
    }
}
