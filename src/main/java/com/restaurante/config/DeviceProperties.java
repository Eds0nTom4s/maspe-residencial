package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.device")
public class DeviceProperties {

    /**
     * Expiração do activation code (minutos).
     */
    private int activationCodeExpirationMinutes = 30;

    /**
     * Secret interno para HMAC-SHA256 (hash de activation code e device token).
     * Em produção, definir via variável de ambiente.
     */
    private String tokenHashSecret;

    /**
     * Limiar (minutos) para considerar heartbeat "stale" (apenas informativo nesta fase).
     */
    private int heartbeatStaleMinutes = 5;

    public int getActivationCodeExpirationMinutes() {
        return activationCodeExpirationMinutes;
    }

    public void setActivationCodeExpirationMinutes(int activationCodeExpirationMinutes) {
        this.activationCodeExpirationMinutes = activationCodeExpirationMinutes;
    }

    public String getTokenHashSecret() {
        return tokenHashSecret;
    }

    public void setTokenHashSecret(String tokenHashSecret) {
        this.tokenHashSecret = tokenHashSecret;
    }

    public int getHeartbeatStaleMinutes() {
        return heartbeatStaleMinutes;
    }

    public void setHeartbeatStaleMinutes(int heartbeatStaleMinutes) {
        this.heartbeatStaleMinutes = heartbeatStaleMinutes;
    }
}

