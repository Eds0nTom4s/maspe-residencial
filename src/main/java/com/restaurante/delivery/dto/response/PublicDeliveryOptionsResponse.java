package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.FulfillmentType;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class PublicDeliveryOptionsResponse {
    boolean tenantDeliveryEnabled;
    List<FulfillmentType> availableFulfillmentTypes;
    Map<Long, Boolean> productDeliveryEligibility;
    boolean customerPickupAvailable;
}

