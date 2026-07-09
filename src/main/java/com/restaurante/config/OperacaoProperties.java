package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@ConfigurationProperties(prefix = "consuma.operacao")
public class OperacaoProperties {

    /**
     * Quando true, as telas e APIs operacionais trabalham no escopo do turno aberto.
     */
    private boolean turnoObrigatorio = true;

    /**
     * Escopo usado por listagens operacionais de pedidos.
     */
    private String pedidosEscopo = "TURNO_ATUAL";

    /**
     * Habilita extrato/relatório operacional por turno.
     */
    private boolean extratoTurnoEnabled = true;

    /**
     * Quando true, exige turno ABERTO para permitir criação de pedidos (QR/POS).
     */
    private boolean requireOpenTurnoForOrders = true;

    /**
     * Quando true, exige turno ABERTO para pedidos criados por device/POS.
     * Default true para operar com disciplina no POS.
     */
    private boolean requireOpenTurnoForDeviceOrders = true;

    /**
     * Quando true, exige turno ABERTO para iniciar pagamento por device/POS.
     * Default true para disciplina de operação assistida.
     */
    private boolean requireOpenTurnoForDevicePayments = true;

    /**
     * Quando true, exige turno ABERTO para confirmação manual de pagamentos (CASH/TPA) por device/POS.
     * Default true para evitar confirmação fora de disciplina operacional.
     */
    private boolean requireOpenTurnoForManualPayments = true;

    /**
     * Limiar em minutos para considerar dispositivo "offline" no pré-fecho (informativo/warning).
     */
    private int deviceOfflineMinutes = 10;

    public boolean isTurnoObrigatorio() {
        return turnoObrigatorio;
    }

    public void setTurnoObrigatorio(boolean turnoObrigatorio) {
        this.turnoObrigatorio = turnoObrigatorio;
    }

    public String getPedidosEscopo() {
        if (pedidosEscopo == null || pedidosEscopo.isBlank()) {
            return "TURNO_ATUAL";
        }
        return pedidosEscopo.trim().toUpperCase(Locale.ROOT);
    }

    public void setPedidosEscopo(String pedidosEscopo) {
        this.pedidosEscopo = pedidosEscopo;
    }

    public boolean isExtratoTurnoEnabled() {
        return extratoTurnoEnabled;
    }

    public void setExtratoTurnoEnabled(boolean extratoTurnoEnabled) {
        this.extratoTurnoEnabled = extratoTurnoEnabled;
    }

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

    public boolean isRequireOpenTurnoForDevicePayments() {
        return requireOpenTurnoForDevicePayments;
    }

    public void setRequireOpenTurnoForDevicePayments(boolean requireOpenTurnoForDevicePayments) {
        this.requireOpenTurnoForDevicePayments = requireOpenTurnoForDevicePayments;
    }

    public boolean isRequireOpenTurnoForManualPayments() {
        return requireOpenTurnoForManualPayments;
    }

    public void setRequireOpenTurnoForManualPayments(boolean requireOpenTurnoForManualPayments) {
        this.requireOpenTurnoForManualPayments = requireOpenTurnoForManualPayments;
    }
}
