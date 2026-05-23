package com.restaurante.service.device.offline.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RegisterLocalActivityOfflineHandler implements DeviceOfflineCommandHandler {

    private final ObjectMapper objectMapper;

    @Override
    public DeviceOfflineCommandType type() {
        return DeviceOfflineCommandType.REGISTER_LOCAL_ACTIVITY;
    }

    @Override
    public DeviceOfflineCommandProcessor.ProcessedResult handle(DevicePrincipal device,
                                                                String commandClientRequestId,
                                                                JsonNode payload,
                                                                String derivedIdempotencyKey,
                                                                String ip,
                                                                String userAgent) {
        JsonNode result = objectMapper.valueToTree(Map.of(
                "ok", true,
                "serverTime", Instant.now().toString()
        ));
        return new DeviceOfflineCommandProcessor.ProcessedResult("DEVICE_OFFLINE_SYNC", null, result);
    }
}

