package com.restaurante.device.capability.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplate;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplateItem;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateItemRepository;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCapabilityTemplateBootstrapService {

    private final DeviceCapabilityTemplateRepository templateRepository;
    private final DeviceCapabilityTemplateItemRepository itemRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void ensureDefaults(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) return;
        ensureTemplate(tenant, "CAP_POS_CAIXA_PADRAO", "POS Caixa (Padrão)", "Defaults POS_CAIXA", OperationalDeviceType.POS_CAIXA,
                List.of(
                        DeviceCapability.CREATE_ORDER,
                        DeviceCapability.CONFIRM_CASH_PAYMENT,
                        DeviceCapability.CONFIRM_TPA_PAYMENT,
                        DeviceCapability.INITIATE_PAYMENT,
                        DeviceCapability.VIEW_PAYMENTS,
                        DeviceCapability.VIEW_PAYMENT_ORDER,
                        DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                        DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.LINK_CUSTOMER_TO_SESSION,
                        DeviceCapability.VIEW_SESSION_PARTICIPANTS,
                        DeviceCapability.ADD_SESSION_PARTICIPANT,
                        DeviceCapability.REMOVE_SESSION_PARTICIPANT,
                        DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS,
                        DeviceCapability.APPROVE_SESSION_PARTICIPANT,
                        DeviceCapability.REJECT_SESSION_PARTICIPANT,
                        DeviceCapability.INVITE_SESSION_PARTICIPANT,
                        DeviceCapability.CANCEL_SESSION_PARTICIPANT_INVITE,
                        DeviceCapability.RESEND_SESSION_PARTICIPANT_INVITE,
                        DeviceCapability.OFFLINE_SYNC,
                        DeviceCapability.OFFLINE_CREATE_ORDER,
                        DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER,
                        DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT
                ));

        ensureTemplate(tenant, "CAP_POS_ATENDIMENTO_PADRAO", "POS Atendimento (Padrão)", "Defaults POS_ATENDIMENTO", OperationalDeviceType.POS_ATENDIMENTO,
                List.of(
                        DeviceCapability.CREATE_ORDER,
                        DeviceCapability.INITIATE_PAYMENT,
                        DeviceCapability.VIEW_ORDERS,
                        DeviceCapability.VIEW_PAYMENTS,
                        DeviceCapability.VIEW_PAYMENT_ORDER,
                        DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                        DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.VIEW_SESSION_PARTICIPANTS,
                        DeviceCapability.ADD_SESSION_PARTICIPANT,
                        DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS,
                        DeviceCapability.INVITE_SESSION_PARTICIPANT,
                        DeviceCapability.RESEND_SESSION_PARTICIPANT_INVITE,
                        DeviceCapability.OFFLINE_SYNC,
                        DeviceCapability.OFFLINE_CREATE_ORDER,
                        DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER
                ));

        ensureTemplate(tenant, "CAP_POS_QUIOSQUE_PADRAO", "POS Quiosque (Padrão)", "Defaults POS_QUIOSQUE", OperationalDeviceType.POS_QUIOSQUE,
                List.of(
                        DeviceCapability.CREATE_ORDER,
                        DeviceCapability.INITIATE_PAYMENT,
                        DeviceCapability.VIEW_ORDERS
                ));

        ensureTemplate(tenant, "CAP_KDS_COZINHA_SEM_IDENTIFICACAO", "KDS Cozinha (Sem Identificação)", "Sem capabilities sensíveis", OperationalDeviceType.KDS_COZINHA,
                List.of(
                        DeviceCapability.VIEW_PRODUCTION,
                        DeviceCapability.UPDATE_PRODUCTION_STATUS
                ));

        ensureTemplate(tenant, "CAP_KDS_BAR_SEM_IDENTIFICACAO", "KDS Bar (Sem Identificação)", "Sem capabilities sensíveis", OperationalDeviceType.KDS_BAR,
                List.of(
                        DeviceCapability.VIEW_PRODUCTION,
                        DeviceCapability.UPDATE_PRODUCTION_STATUS
                ));

        ensureTemplate(tenant, "CAP_ADMIN_TERMINAL_CONTROLADO", "Admin Terminal (Controlado)", "Sem cross-unit por default", OperationalDeviceType.ADMIN_TERMINAL,
                List.of(
                        DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                        DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.LINK_CUSTOMER_TO_SESSION
                ));

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.DEVICE_CAPABILITY_TEMPLATE_DEFAULTS_BOOTSTRAPPED,
                OperationalEntityType.DEVICE_CAPABILITY,
                0L,
                OperationalOrigem.SYSTEM,
                "Defaults de device capability templates garantidos",
                Map.of("tenantId", tenant.getId()),
                null,
                null
        );
    }

    private void ensureTemplate(Tenant tenant,
                                String code,
                                String name,
                                String description,
                                OperationalDeviceType target,
                                List<DeviceCapability> enabledCaps) {
        DeviceCapabilityTemplate existing = templateRepository.findByTenant_IdAndCode(tenant.getId(), code).orElse(null);
        if (existing != null) return;

        DeviceCapabilityTemplate t = new DeviceCapabilityTemplate();
        t.setTenant(tenant);
        t.setCode(code);
        t.setName(name);
        t.setDescription(description);
        t.setTargetDeviceType(target);
        t.setStatus(DeviceCapabilityTemplateStatus.ACTIVE);
        t.setSystemDefault(true);
        t.setVersion(1);
        templateRepository.save(t);

        for (DeviceCapability c : enabledCaps) {
            if (c == DeviceCapability.CROSS_UNIT_ASSISTED_IDENTIFICATION) continue; // nunca em defaults
            DeviceCapabilityTemplateItem item = new DeviceCapabilityTemplateItem();
            item.setTenant(tenant);
            item.setTemplate(t);
            item.setCapability(c);
            item.setEnabled(true);
            itemRepository.save(item);
        }
    }
}
