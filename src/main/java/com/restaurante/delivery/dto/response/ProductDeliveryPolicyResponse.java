package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.PackageSize;
import com.restaurante.model.enums.ProductDeliveryPolicyStatus;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class ProductDeliveryPolicyResponse {
    Long id;
    Long productId;
    boolean deliveryEligible;
    boolean fragile;
    boolean requiresCooling;
    BigDecimal maxDeliveryDistanceKm;
    BigDecimal estimatedPackageWeight;
    PackageSize packageSize;
    boolean allowMotorbikeDelivery;
    boolean allowCarDelivery;
    String notes;
    ProductDeliveryPolicyStatus status;
}

