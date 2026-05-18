package com.restaurante.service.metrics;

import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.dto.response.SyncErrorResponse;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class NoOpDeviceSyncMetricsService implements DeviceSyncMetricsService {

    @Override
    public void recordSyncRequest(String domain, String result) { }

    @Override
    public void recordEtagHit(String domain) { }

    @Override
    public void recordEtagMiss(String domain) { }

    @Override
    public void recordFullSyncRequired(String domain, SyncEnvelope.FullSyncRequiredReason reason) { }

    @Override
    public void recordCursorError(String domain, SyncErrorResponse.SyncErrorCode code) { }

    @Override
    public void recordDeviceAuth(String result) { }

    @Override
    public void recordHeartbeat(String result) { }

    @Override
    public <T> T timeSync(String domain, Supplier<T> supplier) {
        return supplier.get();
    }
}
