package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.device.orders")
public class DeviceOrdersProperties {

    private int maxItems = 100;
    private int maxObservacaoLength = 500;
    private int defaultLookbackHours = 12;

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getMaxObservacaoLength() {
        return maxObservacaoLength;
    }

    public void setMaxObservacaoLength(int maxObservacaoLength) {
        this.maxObservacaoLength = maxObservacaoLength;
    }

    public int getDefaultLookbackHours() {
        return defaultLookbackHours;
    }

    public void setDefaultLookbackHours(int defaultLookbackHours) {
        this.defaultLookbackHours = defaultLookbackHours;
    }
}

