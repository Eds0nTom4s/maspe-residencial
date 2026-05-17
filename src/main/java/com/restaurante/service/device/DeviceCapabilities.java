package com.restaurante.service.device;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoTipo;

import java.util.EnumSet;
import java.util.List;

public final class DeviceCapabilities {

    private DeviceCapabilities() {}

    public static List<DeviceCapability> forTipo(DispositivoTipo tipo) {
        if (tipo == null) tipo = DispositivoTipo.OUTRO;

        return switch (tipo) {
            case POS -> List.copyOf(EnumSet.of(
                    DeviceCapability.HEARTBEAT,
                    DeviceCapability.SYNC_CATALOG,
                    DeviceCapability.VIEW_ORDERS,
                    DeviceCapability.VIEW_PAYMENTS
            ));
            case KDS, COZINHA, BAR -> List.copyOf(EnumSet.of(
                    DeviceCapability.HEARTBEAT,
                    DeviceCapability.VIEW_PRODUCTION,
                    DeviceCapability.UPDATE_PRODUCTION_STATUS
            ));
            case CHECKOUT -> List.copyOf(EnumSet.of(
                    DeviceCapability.HEARTBEAT,
                    DeviceCapability.VIEW_ORDERS,
                    DeviceCapability.VIEW_PAYMENTS
            ));
            case QUIOSQUE -> List.copyOf(EnumSet.of(
                    DeviceCapability.HEARTBEAT,
                    DeviceCapability.SYNC_CATALOG,
                    DeviceCapability.VIEW_ORDERS
            ));
            default -> List.copyOf(EnumSet.of(DeviceCapability.HEARTBEAT));
        };
    }
}

