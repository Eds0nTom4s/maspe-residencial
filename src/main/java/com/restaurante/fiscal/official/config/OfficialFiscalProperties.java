package com.restaurante.fiscal.official.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consuma.fiscal.official")
public class OfficialFiscalProperties {
    private boolean enabled = false;
    private boolean workerEnabled = false;
    private boolean allowProduction = false;
    private boolean simulationEnabled = false;

    private int maxAttempts = 5;
    private int initialDelaySeconds = 30;
    private int retryBackoffSeconds = 120;
    private int maxBackoffSeconds = 3600;
    private int batchSize = 50;
    private int staleLockMinutes = 10;
    private String workerId = "local";
}

