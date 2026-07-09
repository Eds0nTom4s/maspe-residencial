package com.restaurante.config;

import com.restaurante.model.enums.DeviceOfflineCommandType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "consuma.device.offline-sync")
public class DeviceOfflineSyncProperties {

    private boolean enabled = true;
    private int maxBatchSize = 100;
    private int maxOfflineAgeMinutes = 720;
    private int maxBatchPayloadBytes = 1_048_576; // 1 MiB
    private int maxCommandPayloadBytes = 65_536;  // 64 KiB
    private int maxPedidoItems = 50;
    private int maxLocalRefDepth = 3;
    private boolean allowForwardLocalRefs = false;
    private boolean rejectPriceChanges = true;
    private boolean requireOpenTurnoForManualPayment = true;
    private Set<DeviceOfflineCommandType> allowedCommandTypes = EnumSet.of(
            DeviceOfflineCommandType.CREATE_PEDIDO_POS,
            DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL,
            DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT,
            DeviceOfflineCommandType.REGISTER_LOCAL_ACTIVITY
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxOfflineAgeMinutes() {
        return maxOfflineAgeMinutes;
    }

    public void setMaxOfflineAgeMinutes(int maxOfflineAgeMinutes) {
        this.maxOfflineAgeMinutes = maxOfflineAgeMinutes;
    }

    public int getMaxBatchPayloadBytes() {
        return maxBatchPayloadBytes;
    }

    public void setMaxBatchPayloadBytes(int maxBatchPayloadBytes) {
        this.maxBatchPayloadBytes = maxBatchPayloadBytes;
    }

    public int getMaxCommandPayloadBytes() {
        return maxCommandPayloadBytes;
    }

    public void setMaxCommandPayloadBytes(int maxCommandPayloadBytes) {
        this.maxCommandPayloadBytes = maxCommandPayloadBytes;
    }

    public int getMaxPedidoItems() {
        return maxPedidoItems;
    }

    public void setMaxPedidoItems(int maxPedidoItems) {
        this.maxPedidoItems = maxPedidoItems;
    }

    public int getMaxLocalRefDepth() {
        return maxLocalRefDepth;
    }

    public void setMaxLocalRefDepth(int maxLocalRefDepth) {
        this.maxLocalRefDepth = maxLocalRefDepth;
    }

    public boolean isAllowForwardLocalRefs() {
        return allowForwardLocalRefs;
    }

    public void setAllowForwardLocalRefs(boolean allowForwardLocalRefs) {
        this.allowForwardLocalRefs = allowForwardLocalRefs;
    }

    public boolean isRejectPriceChanges() {
        return rejectPriceChanges;
    }

    public void setRejectPriceChanges(boolean rejectPriceChanges) {
        this.rejectPriceChanges = rejectPriceChanges;
    }

    public boolean isRequireOpenTurnoForManualPayment() {
        return requireOpenTurnoForManualPayment;
    }

    public void setRequireOpenTurnoForManualPayment(boolean requireOpenTurnoForManualPayment) {
        this.requireOpenTurnoForManualPayment = requireOpenTurnoForManualPayment;
    }

    public Set<DeviceOfflineCommandType> getAllowedCommandTypes() {
        return allowedCommandTypes;
    }

    public void setAllowedCommandTypes(Set<DeviceOfflineCommandType> allowedCommandTypes) {
        this.allowedCommandTypes = allowedCommandTypes;
    }
}
