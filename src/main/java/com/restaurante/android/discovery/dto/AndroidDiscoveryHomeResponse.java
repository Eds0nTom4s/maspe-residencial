package com.restaurante.android.discovery.dto;

import java.util.List;

public record AndroidDiscoveryHomeResponse(
        List<Object> categories,
        AndroidMerchantSectionResponse nearby,
        AndroidMerchantSectionResponse recommended,
        AndroidMerchantSectionResponse featured) {

    public AndroidDiscoveryHomeResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
