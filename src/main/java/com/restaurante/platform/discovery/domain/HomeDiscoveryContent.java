package com.restaurante.platform.discovery.domain;

import java.util.List;

public record HomeDiscoveryContent(
        List<MerchantCategory> categories,
        MerchantSection nearby,
        MerchantSection recommended,
        MerchantSection featured) {

    public HomeDiscoveryContent {
        categories = List.copyOf(categories);
    }
}
