package com.restaurante.delivery.dto.request;

import com.restaurante.model.enums.DeliveryCancelAllowedUntilStatus;
import com.restaurante.model.enums.DeliveryMode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTenantDeliveryPolicyRequest {
    private boolean deliveryEnabled;
    private DeliveryMode deliveryMode;
    private boolean acceptsConsumaNetwork;
    private boolean acceptsTenantOwnDelivery;
    private boolean allowCustomerPickup;
    private boolean requirePaymentBeforeDelivery;
    private boolean autoCreateDeliveryJobAfterPayment;
    private BigDecimal maxDeliveryDistanceKm;
    private Integer preparationTimeMinutes;
    private DeliveryCancelAllowedUntilStatus cancelAllowedUntilStatus;
    private String deliveryNotes;
}

