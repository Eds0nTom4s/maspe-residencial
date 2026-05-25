package com.restaurante.delivery.dto.request;

import com.restaurante.model.enums.PackageSize;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductDeliveryPolicyRequest {
    private boolean deliveryEligible;
    private boolean fragile;
    private boolean requiresCooling;
    private BigDecimal maxDeliveryDistanceKm;
    private BigDecimal estimatedPackageWeight;
    private PackageSize packageSize;
    private boolean allowMotorbikeDelivery = true;
    private boolean allowCarDelivery = true;
    private String notes;
}

