package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.payment.order")
public class PaymentOrderProperties {

    private int expirationMinutes = 10;

    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(int expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public int effectiveExpirationMinutes() {
        return Math.max(1, expirationMinutes);
    }
}
