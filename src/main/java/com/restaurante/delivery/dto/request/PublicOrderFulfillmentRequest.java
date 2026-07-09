package com.restaurante.delivery.dto.request;

import com.restaurante.model.enums.FulfillmentType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PublicOrderFulfillmentRequest {
    private FulfillmentType fulfillmentType;
    private String customerName;
    private String customerPhone;
    private String deliveryAddressText;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private String deliveryNotes;
}

