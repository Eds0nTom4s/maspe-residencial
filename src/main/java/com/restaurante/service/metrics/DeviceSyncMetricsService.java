package com.restaurante.service.metrics;

import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.dto.response.SyncErrorResponse;

import java.util.function.Supplier;

public interface DeviceSyncMetricsService {

    void recordSyncRequest(String domain, String result);

    void recordEtagHit(String domain);

    void recordEtagMiss(String domain);

    void recordFullSyncRequired(String domain, SyncEnvelope.FullSyncRequiredReason reason);

    void recordCursorError(String domain, SyncErrorResponse.SyncErrorCode code);

    void recordDeviceAuth(String result);

    void recordHeartbeat(String result);

    <T> T timeSync(String domain, Supplier<T> supplier);
}

