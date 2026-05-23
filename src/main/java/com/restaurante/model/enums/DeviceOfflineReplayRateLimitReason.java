package com.restaurante.model.enums;

public enum DeviceOfflineReplayRateLimitReason {
    TENANT_ACTIVE_OPERATION_LIMIT,
    TENANT_HOURLY_REPLAY_LIMIT,
    TOO_MANY_COMMANDS,
    OPERATION_ALREADY_RUNNING_FOR_SESSION
}

