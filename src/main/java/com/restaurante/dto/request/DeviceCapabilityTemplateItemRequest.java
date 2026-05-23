package com.restaurante.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.model.enums.DeviceCapability;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceCapabilityTemplateItemRequest {
    @NotNull
    private DeviceCapability capability;
    @NotNull
    private Boolean enabled;
    private String overrideReason;
    private JsonNode metadata;
}

