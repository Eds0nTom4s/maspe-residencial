package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapabilityOverwriteMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutMode;
import lombok.Data;

import java.util.List;

@Data
public class DeviceCapabilityRolloutPreviewResponse {
    private Long templateId;
    private Long unidadeId;
    private DeviceCapabilityRolloutMode rolloutMode;
    private DeviceCapabilityOverwriteMode overwriteMode;
    private int totalDevicesTargeted;
    private int totalCapabilitiesToCreate;
    private int totalCapabilitiesToUpdate;
    private int totalCapabilitiesToSkip;
    private List<String> warnings;
    private List<DeviceCapabilityRolloutResultResponse> deviceResults;
}

