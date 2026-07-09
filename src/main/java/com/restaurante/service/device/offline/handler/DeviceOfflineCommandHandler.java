package com.restaurante.service.device.offline.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;

public interface DeviceOfflineCommandHandler {
    DeviceOfflineCommandType type();

    DeviceOfflineCommandProcessor.ProcessedResult handle(DevicePrincipal device,
                                                         String commandClientRequestId,
                                                         JsonNode payload,
                                                         String derivedIdempotencyKey,
                                                         String ip,
                                                         String userAgent);
}

