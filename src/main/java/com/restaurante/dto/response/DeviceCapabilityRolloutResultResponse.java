package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalDeviceType;
import lombok.Data;

import java.util.List;

@Data
public class DeviceCapabilityRolloutResultResponse {
    private Long deviceId;
    private String deviceName;
    private OperationalDeviceType deviceType;
    private List<String> capabilitiesToCreate;
    private List<String> capabilitiesToUpdate;
    private List<String> capabilitiesSkipped;
    private List<String> warnings;
    private List<String> errors;
}

