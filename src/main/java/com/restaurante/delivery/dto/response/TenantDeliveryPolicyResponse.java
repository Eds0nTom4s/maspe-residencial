package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.DeliveryCancelAllowedUntilStatus;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class TenantDeliveryPolicyResponse {
    Long id;
    boolean deliveryEnabled;
    DeliveryMode deliveryMode;
    boolean acceptsConsumaNetwork;
    boolean acceptsTenantOwnDelivery;
    boolean allowCustomerPickup;
    boolean requirePaymentBeforeDelivery;
    boolean autoCreateDeliveryJobAfterPayment;
    BigDecimal maxDeliveryDistanceKm;
    Integer preparationTimeMinutes;
    DeliveryCancelAllowedUntilStatus cancelAllowedUntilStatus;
    String deliveryNotes;
    TenantDeliveryPolicyStatus status;
}

