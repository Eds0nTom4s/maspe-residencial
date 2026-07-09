package com.restaurante.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceHeartbeatRequest {

    @Size(max = 40)
    private String appVersion;

    private Boolean online;

    @Min(0)
    @Max(100)
    private Integer batteryLevel;

    @Size(max = 40)
    private String localTime;
}

