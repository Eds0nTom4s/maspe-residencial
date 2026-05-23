package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapabilityRolloutStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DeviceCapabilityRolloutApplyResponse {
    private Long rolloutId;
    private Long templateId;
    private Long unidadeId;
    private DeviceCapabilityRolloutStatus status;
    private int totalDevicesTargeted;
    private int totalCapabilitiesCreated;
    private int totalCapabilitiesUpdated;
    private int totalCapabilitiesSkipped;
    private int totalErrors;
    private Instant startedAt;
    private Instant finishedAt;
    private List<DeviceCapabilityRolloutResultResponse> deviceResults;
}

