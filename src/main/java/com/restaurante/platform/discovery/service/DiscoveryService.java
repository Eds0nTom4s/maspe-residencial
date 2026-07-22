package com.restaurante.platform.discovery.service;

import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;

public interface DiscoveryService {

    DiscoveryResult<DiscoveryHomeResponse> home(HomeDiscoveryRequest request);

    DiscoveryResult<MerchantSearchResponse> search(SearchDiscoveryRequest request);

    DiscoveryResult<MerchantOverviewResponse> merchant(MerchantRequest request);
}
