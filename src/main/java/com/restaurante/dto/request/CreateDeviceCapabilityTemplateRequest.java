package com.restaurante.dto.request;

import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalDeviceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateDeviceCapabilityTemplateRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    private OperationalDeviceType targetDeviceType;
    @NotNull
    private DeviceCapabilityTemplateStatus status;
    @Valid
    private List<DeviceCapabilityTemplateItemRequest> items;
}

