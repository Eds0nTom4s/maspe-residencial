package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapability;
import lombok.Data;

import java.time.Instant;

@Data
public class DeviceCapabilityResponse {
    private DeviceCapability capability;
    private boolean enabled;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;
}

