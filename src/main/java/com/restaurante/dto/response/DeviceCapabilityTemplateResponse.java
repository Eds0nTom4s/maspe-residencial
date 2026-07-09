package com.restaurante.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalDeviceType;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DeviceCapabilityTemplateResponse {
    private Long templateId;
    private String code;
    private String name;
    private String description;
    private OperationalDeviceType targetDeviceType;
    private DeviceCapabilityTemplateStatus status;
    private boolean systemDefault;
    private int version;
    private List<DeviceCapabilityTemplateItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class DeviceCapabilityTemplateItemResponse {
        private Long id;
        private com.restaurante.model.enums.DeviceCapability capability;
        private boolean enabled;
        private String overrideReason;
        private JsonNode metadata;
    }
}

