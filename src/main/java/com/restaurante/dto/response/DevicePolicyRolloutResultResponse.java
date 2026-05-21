package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalDeviceType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DevicePolicyRolloutResultResponse {
    private Long deviceId;
    private String deviceName;
    private OperationalDeviceType deviceType;
    private List<String> policiesToCreate;
    private List<String> policiesToUpdate;
    private List<String> policiesSkipped;
    private List<String> warnings;
    private List<String> errors;
}

