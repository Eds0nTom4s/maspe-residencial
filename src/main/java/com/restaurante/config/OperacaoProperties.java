package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.operacao")
public class OperacaoProperties {

    /**
     * Quando true, exige turno ABERTO para permitir criação de pedidos (QR/POS).
     * Default false para não quebrar fluxo público enquanto a disciplina é adotada.
     */
    private boolean requireOpenTurnoForOrders = false;

    /**
     * Quando true, exige turno ABERTO para pedidos criados por device/POS.
     * Default true para operar com disciplina no POS.
     */
    private boolean requireOpenTurnoForDeviceOrders = true;

    /**
     * Limiar em minutos para considerar dispositivo "offline" no pré-fecho (informativo/warning).
     */
    private int deviceOfflineMinutes = 10;

    public boolean isRequireOpenTurnoForOrders() {
        return requireOpenTurnoForOrders;
    }

    public void setRequireOpenTurnoForOrders(boolean requireOpenTurnoForOrders) {
        this.requireOpenTurnoForOrders = requireOpenTurnoForOrders;
    }

    public int getDeviceOfflineMinutes() {
        return deviceOfflineMinutes;
    }

    public void setDeviceOfflineMinutes(int deviceOfflineMinutes) {
        this.deviceOfflineMinutes = deviceOfflineMinutes;
    }

    public boolean isRequireOpenTurnoForDeviceOrders() {
        return requireOpenTurnoForDeviceOrders;
    }

    public void setRequireOpenTurnoForDeviceOrders(boolean requireOpenTurnoForDeviceOrders) {
        this.requireOpenTurnoForDeviceOrders = requireOpenTurnoForDeviceOrders;
    }
}
