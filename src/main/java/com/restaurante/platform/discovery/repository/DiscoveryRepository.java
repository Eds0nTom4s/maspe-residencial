package com.restaurante.platform.discovery.repository;

import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;

public interface DiscoveryRepository {

    DiscoveryResult<HomeDiscoveryContent> home(HomeDiscoveryRequest request);

    DiscoveryResult<MerchantSearchContent> search(SearchDiscoveryRequest request);

    DiscoveryResult<MerchantOverview> merchant(MerchantRequest request);
}
