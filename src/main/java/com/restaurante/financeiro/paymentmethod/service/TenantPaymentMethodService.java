package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.PaymentUsageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantPaymentMethodService {

    private final TenantPaymentMethodRepository repository;
    private final TenantPaymentMethodBootstrapService bootstrapService;
    private final AppyPayProperties appyPayProperties;

    @Value("${consuma.financeiro.payment-methods.allow-no-active-method:false}")
    private boolean allowNoActiveMethod;

    @Transactional
    public void ensureDefaultsForTenant(Long tenantId) {
        bootstrapService.ensureDefaults(tenantId);
    }

    @Transactional
    public List<TenantPaymentMethod> listForTenant(Long tenantId) {
        ensureDefaultsForTenant(tenantId);
        return repository.findByTenantIdOrderBySortOrderAscCodeAsc(tenantId);
    }

    @Transactional
    public TenantPaymentMethod getOrThrow(Long tenantId, PaymentMethodCode code) {
        ensureDefaultsForTenant(tenantId);
        return repository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não configurado."));
    }

    @Transactional
    public List<TenantPaymentMethod> listAvailableForContext(Long tenantId,
                                                             PaymentUsageContext context,
                                                             PaymentDestination destination) {
        ensureDefaultsForTenant(tenantId);
        return repository.findByTenantIdAndStatusOrderBySortOrderAscCodeAsc(tenantId, PaymentMethodStatus.ACTIVE)
                .stream()
                .filter(m -> isAllowedForContext(m, context))
                .filter(m -> isAllowedForDestination(m, destination))
                .toList();
    }

    @Transactional
    public TenantPaymentMethod validateMethodAllowed(Long tenantId,
                                                     PaymentMethodCode code,
                                                     PaymentUsageContext context,
                                                     PaymentDestination destination,
                                                     BigDecimal amount) {
        TenantPaymentMethod m = getOrThrow(tenantId, code);

        if (m.getStatus() != PaymentMethodStatus.ACTIVE) {
            throw new BusinessException("Método de pagamento inativo para este tenant.");
        }
        if (!isAllowedForContext(m, context)) {
            throw new BusinessException("Método de pagamento não permitido para este canal.");
        }
        if (!isAllowedForDestination(m, destination)) {
            throw new BusinessException("Método de pagamento não permitido para este destino.");
        }
        if (m.isRequiresGateway()) {
            if (!isAppyPayConfigured()) {
                throw new BusinessException("Método gateway indisponível: configuração do AppyPay ausente.");
            }
        }
        if (amount != null) {
            if (m.getMinAmount() != null && amount.compareTo(m.getMinAmount()) < 0) {
                throw new BusinessException("Valor abaixo do mínimo permitido para este método.");
            }
            if (m.getMaxAmount() != null && amount.compareTo(m.getMaxAmount()) > 0) {
                throw new BusinessException("Valor acima do máximo permitido para este método.");
            }
        }
        return m;
    }

    private boolean isAppyPayConfigured() {
        if (appyPayProperties == null) return false;
        if (appyPayProperties.isMock()) return true;
        return appyPayProperties.getBaseUrl() != null && !appyPayProperties.getBaseUrl().isBlank()
                && appyPayProperties.getTokenUrl() != null && !appyPayProperties.getTokenUrl().isBlank()
                && appyPayProperties.getClientId() != null && !appyPayProperties.getClientId().isBlank()
                && appyPayProperties.getClientSecret() != null && !appyPayProperties.getClientSecret().isBlank()
                && appyPayProperties.getResource() != null && !appyPayProperties.getResource().isBlank();
    }

    private boolean isAllowedForContext(TenantPaymentMethod m, PaymentUsageContext context) {
        if (context == null) return false;
        return switch (context) {
            case TENANT_ADMIN -> true;
            case QR_PUBLICO -> m.isEnabledForQr();
            case DEVICE_POS -> m.isEnabledForPos();
        };
    }

    private boolean isAllowedForDestination(TenantPaymentMethod m, PaymentDestination destination) {
        if (destination == null) return false;
        return switch (destination) {
            case PEDIDO -> m.isEnabledForPedido();
            case FUNDO_CONSUMO -> m.isEnabledForFundoConsumo();
        };
    }
}
