package com.restaurante.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class DeviceOfflineCommandRequest {

    @NotBlank
    private String clientRequestId;

    @NotNull
    private DeviceOfflineCommandType commandType;

    @NotBlank
    private String commandVersion;

    private Instant localCreatedAt;
    private Long localSequence;

    @NotNull
    private JsonNode payload;

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public DeviceOfflineCommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(DeviceOfflineCommandType commandType) {
        this.commandType = commandType;
    }

    public String getCommandVersion() {
        return commandVersion;
    }

    public void setCommandVersion(String commandVersion) {
        this.commandVersion = commandVersion;
    }

    public Instant getLocalCreatedAt() {
        return localCreatedAt;
    }

    public void setLocalCreatedAt(Instant localCreatedAt) {
        this.localCreatedAt = localCreatedAt;
    }

    public Long getLocalSequence() {
        return localSequence;
    }

    public void setLocalSequence(Long localSequence) {
        this.localSequence = localSequence;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}

