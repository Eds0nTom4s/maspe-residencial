package com.restaurante.device.capability.service;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalDeviceType;

import java.util.EnumSet;
import java.util.Set;

public final class DeviceCapabilityDefaults {

    private DeviceCapabilityDefaults() {}

    public static Set<DeviceCapability> additionalDefaultsByOperationalType(OperationalDeviceType type) {
        if (type == null) type = OperationalDeviceType.GENERIC_DEVICE;
        return switch (type) {
            case POS_CAIXA -> EnumSet.of(
                    DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                    DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.LINK_CUSTOMER_TO_SESSION,
                    DeviceCapability.OFFLINE_SYNC,
                    DeviceCapability.OFFLINE_CREATE_ORDER,
                    DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER,
                    DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT
            );
            case POS_ATENDIMENTO -> EnumSet.of(
                    DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                    DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.OFFLINE_SYNC,
                    DeviceCapability.OFFLINE_CREATE_ORDER,
                    DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER
            );
            case POS_QUIOSQUE, KDS_COZINHA, KDS_BAR, DISPLAY_SENHA, ADMIN_TERMINAL, GENERIC_DEVICE -> EnumSet.noneOf(DeviceCapability.class);
        };
    }
}
