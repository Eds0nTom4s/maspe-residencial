package com.restaurante.service.device.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.offline.handler.DeviceOfflineCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DeviceOfflineCommandProcessor {

    public record ProcessedResult(String createdEntityType, Long createdEntityId, JsonNode resultJson) {}

    private final Map<DeviceOfflineCommandType, DeviceOfflineCommandHandler> handlers;

    public DeviceOfflineCommandProcessor(List<DeviceOfflineCommandHandler> handlerList) {
        EnumMap<DeviceOfflineCommandType, DeviceOfflineCommandHandler> map = new EnumMap<>(DeviceOfflineCommandType.class);
        for (DeviceOfflineCommandHandler h : handlerList) {
            if (h != null && h.type() != null) map.put(h.type(), h);
        }
        this.handlers = map;
    }

    public ProcessedResult process(DevicePrincipal device,
                                   DeviceOfflineCommandType type,
                                   String commandClientRequestId,
                                   JsonNode payload,
                                   String derivedIdempotencyKey,
                                   String ip,
                                   String userAgent) {
        DeviceOfflineCommandHandler handler = handlers.get(type);
        if (handler == null) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    com.restaurante.dto.response.DeviceErrorResponse.DeviceErrorCode.OFFLINE_COMMAND_TYPE_NOT_ALLOWED,
                    "Tipo de comando não permitido offline.",
                    false,
                    com.restaurante.dto.response.DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        return handler.handle(device, commandClientRequestId, payload, derivedIdempotencyKey, ip, userAgent);
    }
}

