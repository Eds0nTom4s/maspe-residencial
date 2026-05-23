package com.restaurante.dto.request;

import com.restaurante.model.enums.DeviceCapabilityOverwriteMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutMode;
import com.restaurante.model.enums.OperationalDeviceType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class DeviceCapabilityRolloutRequest {
    @NotNull
    private Long unidadeId;
    @NotNull
    private DeviceCapabilityRolloutMode rolloutMode;
    private OperationalDeviceType targetDeviceType;
    private Set<Long> selectedDeviceIds;
    @NotNull
    private DeviceCapabilityOverwriteMode overwriteMode;
}

