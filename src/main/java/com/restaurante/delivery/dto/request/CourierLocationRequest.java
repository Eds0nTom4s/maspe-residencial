package com.restaurante.delivery.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourierLocationRequest {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;
}

