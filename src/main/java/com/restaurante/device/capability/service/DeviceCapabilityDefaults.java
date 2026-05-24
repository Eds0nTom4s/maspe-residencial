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
                    DeviceCapability.OPEN_OPERATOR_CASH_SESSION,
                    DeviceCapability.CLOSE_OPERATOR_CASH_SESSION,
                    DeviceCapability.VIEW_OPERATOR_CASH_SESSION,
                    DeviceCapability.VIEW_OPERATOR_CASH_SESSION_ITEMS,
                    DeviceCapability.VIEW_OPERATOR_CASH_DIVERGENCE,
                    DeviceCapability.JUSTIFY_OPERATOR_CASH_DIVERGENCE,
                    DeviceCapability.SUBMIT_OPERATOR_CASH_DIVERGENCE,
                    DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                    DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.LINK_CUSTOMER_TO_SESSION,
                    DeviceCapability.OFFLINE_SYNC,
                    DeviceCapability.OFFLINE_CREATE_ORDER,
                    DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER,
                    DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT,
                    DeviceCapability.VIEW_SESSION_PARTICIPANTS,
                    DeviceCapability.ADD_SESSION_PARTICIPANT,
                    DeviceCapability.REMOVE_SESSION_PARTICIPANT,
                    DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS,
                    DeviceCapability.APPROVE_SESSION_PARTICIPANT,
                    DeviceCapability.REJECT_SESSION_PARTICIPANT,
                    DeviceCapability.INVITE_SESSION_PARTICIPANT,
                    DeviceCapability.CANCEL_SESSION_PARTICIPANT_INVITE,
                    DeviceCapability.RESEND_SESSION_PARTICIPANT_INVITE
            );
            case POS_ATENDIMENTO -> EnumSet.of(
                    DeviceCapability.VIEW_OPERATOR_CASH_SESSION,
                    DeviceCapability.VIEW_OPERATOR_CASH_DIVERGENCE,
                    DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                    DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                    DeviceCapability.OFFLINE_SYNC,
                    DeviceCapability.OFFLINE_CREATE_ORDER,
                    DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER,
                    DeviceCapability.VIEW_SESSION_PARTICIPANTS,
                    DeviceCapability.ADD_SESSION_PARTICIPANT,
                    DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS,
                    DeviceCapability.INVITE_SESSION_PARTICIPANT,
                    DeviceCapability.RESEND_SESSION_PARTICIPANT_INVITE
            );
            case POS_QUIOSQUE, KDS_COZINHA, KDS_BAR, DISPLAY_SENHA, ADMIN_TERMINAL, GENERIC_DEVICE -> EnumSet.noneOf(DeviceCapability.class);
        };
    }
}
