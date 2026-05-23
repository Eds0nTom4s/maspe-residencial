package com.restaurante.device.offline.job;

import com.restaurante.config.DeviceOfflineReplayAsyncProperties;
import com.restaurante.service.tenant.offline.DeviceOfflineReplayAsyncWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "consuma.device.offline-replay.async", name = "worker-enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class DeviceOfflineReplayAsyncJob {

    private final DeviceOfflineReplayAsyncProperties props;
    private final DeviceOfflineReplayAsyncWorkerService workerService;

    @Scheduled(cron = "${consuma.device.offline-replay.async.worker-cron:*/30 * * * * *}")
    public void run() {
        if (!props.isEnabled() || !props.isWorkerEnabled()) return;
        try {
            workerService.processOneEligibleOperation();
        } catch (Exception e) {
            log.warn("Job de replay async falhou: {}", e.getMessage());
        }
    }
}

