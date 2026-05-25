package com.restaurante.delivery.dto.response;

import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierStatus;
import com.restaurante.model.enums.CourierVehicleType;
import com.restaurante.model.enums.CourierVerificationStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
public class CourierProfileResponse {
    Long id;
    String courierCode;
    String fullName;
    String phoneMasked;
    CourierStatus status;
    CourierVerificationStatus verificationStatus;
    CourierVehicleType vehicleType;
    String vehiclePlateMasked;
    boolean hasOwnVehicle;
    boolean acceptsTerms;
    CourierAvailability currentAvailability;
    BigDecimal currentLatitude;
    BigDecimal currentLongitude;
    LocalDateTime lastLocationUpdateAt;
    Long activeDeliveryJobId;
}

