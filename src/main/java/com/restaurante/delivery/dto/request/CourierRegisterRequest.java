package com.restaurante.delivery.dto.request;

import com.restaurante.model.enums.CourierVehicleType;
import lombok.Data;

@Data
public class CourierRegisterRequest {
    private String username;
    private String password;
    private String fullName;
    private String phone;
    private CourierVehicleType vehicleType;
    private String vehiclePlate;
    private boolean acceptsTerms;
}

