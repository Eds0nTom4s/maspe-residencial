package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentConfirmationMode;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.PaymentMethodType;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantPaymentMethodBootstrapService {

    private final TenantRepository tenantRepository;
    private final TenantPaymentMethodRepository repository;
    private final OperationalEventLogService operationalEventLogService;
    private final AppyPayProperties appyPayProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaults(Long tenantId) {
        if (tenantId == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        if (repository.existsByTenantId(tenantId)) return;

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        ensureDefaultsInternal(tenant);
    }

    @Transactional
    public void ensureDefaultsInCurrentTransaction(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        if (repository.existsByTenantId(tenant.getId())) return;
        ensureDefaultsInternal(tenant);
    }

    private void ensureDefaultsInternal(Tenant tenant) {
        TenantPaymentMethod cash = new TenantPaymentMethod();
        cash.setTenant(tenant);
        cash.setCode(PaymentMethodCode.CASH);
        cash.setDisplayName("Dinheiro");
        cash.setDescription("Pagamento em numerário (confirmação manual no POS).");
        cash.setStatus(PaymentMethodStatus.ACTIVE);
        cash.setType(PaymentMethodType.MANUAL_PHYSICAL);
        cash.setConfirmationMode(PaymentConfirmationMode.MANUAL_DEVICE);
        cash.setProvider(PaymentMethodProvider.NONE);
        cash.setEnabledForQr(true);
        cash.setEnabledForPos(true);
        cash.setEnabledForPedido(true);
        cash.setEnabledForFundoConsumo(true);
        cash.setRequiresOpenTurno(true);
        cash.setRequiresGateway(false);
        cash.setRequiresManualConfirmation(true);
        cash.setCurrency("AOA");
        cash.setSortOrder(10);

        TenantPaymentMethod tpa = new TenantPaymentMethod();
        tpa.setTenant(tenant);
        tpa.setCode(PaymentMethodCode.TPA);
        tpa.setDisplayName("TPA");
        tpa.setDescription("Pagamento em TPA físico (confirmação manual no POS).");
        tpa.setStatus(PaymentMethodStatus.ACTIVE);
        tpa.setType(PaymentMethodType.MANUAL_PHYSICAL);
        tpa.setConfirmationMode(PaymentConfirmationMode.MANUAL_DEVICE);
        tpa.setProvider(PaymentMethodProvider.NONE);
        tpa.setEnabledForQr(true);
        tpa.setEnabledForPos(true);
        tpa.setEnabledForPedido(true);
        tpa.setEnabledForFundoConsumo(true);
        tpa.setRequiresOpenTurno(true);
        tpa.setRequiresGateway(false);
        tpa.setRequiresManualConfirmation(true);
        tpa.setCurrency("AOA");
        tpa.setSortOrder(20);

        TenantPaymentMethod appy = new TenantPaymentMethod();
        appy.setTenant(tenant);
        appy.setCode(PaymentMethodCode.APPYPAY);
        appy.setDisplayName("AppyPay");
        appy.setDescription("Pagamento digital via gateway (confirmação por callback/polling).");
        boolean appyConfigured = appyPayProperties != null && (appyPayProperties.isMock()
                || (appyPayProperties.getClientId() != null && !appyPayProperties.getClientId().isBlank()));
        appy.setStatus(appyConfigured ? PaymentMethodStatus.ACTIVE : PaymentMethodStatus.INACTIVE);
        appy.setType(PaymentMethodType.DIGITAL_GATEWAY);
        appy.setConfirmationMode(PaymentConfirmationMode.GATEWAY_CALLBACK_POLLING);
        appy.setProvider(PaymentMethodProvider.APPYPAY);
        appy.setEnabledForQr(appyConfigured);
        appy.setEnabledForPos(appyConfigured);
        appy.setEnabledForPedido(true);
        appy.setEnabledForFundoConsumo(true);
        appy.setRequiresOpenTurno(false);
        appy.setRequiresGateway(true);
        appy.setRequiresManualConfirmation(false);
        appy.setCurrency("AOA");
        appy.setSortOrder(30);

        List<TenantPaymentMethod> saved = repository.saveAll(List.of(cash, tpa, appy));

        // Auditoria: registrar 1 evento por bootstrap (sanitizado)
        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.PAYMENT_METHOD_DEFAULTS_BOOTSTRAPPED,
                OperationalEntityType.TENANT_PAYMENT_METHOD,
                saved.getFirst().getId(),
                OperationalOrigem.SYSTEM,
                "Métodos de pagamento padrão bootstrapados para o tenant",
                Map.of(
                        "methods", List.of(
                                Map.of("code", PaymentMethodCode.CASH.name(), "status", PaymentMethodStatus.ACTIVE.name()),
                                Map.of("code", PaymentMethodCode.TPA.name(), "status", PaymentMethodStatus.ACTIVE.name()),
                                Map.of("code", PaymentMethodCode.APPYPAY.name(), "status", appy.getStatus().name())
                        )
                ),
                null,
                null
        );
    }
}
