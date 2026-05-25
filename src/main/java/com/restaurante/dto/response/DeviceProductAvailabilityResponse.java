package com.restaurante.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeviceProductAvailabilityResponse {
    private Long productId;
    private Boolean available;
    private BigDecimal estimatedAvailableQuantity;
    private String stockPolicy;
    private String warning;
}

