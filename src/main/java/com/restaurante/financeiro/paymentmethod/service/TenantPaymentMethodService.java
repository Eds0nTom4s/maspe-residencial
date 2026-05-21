package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
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

    @Value("${consuma.financeiro.payment-methods.allow-no-active-method:false}")
    private boolean allowNoActiveMethod;

    @Transactional
    public void ensureDefaultsForTenant(Long tenantId) {
        bootstrapService.ensureDefaults(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TenantPaymentMethod> listForTenant(Long tenantId) {
        ensureDefaultsForTenant(tenantId);
        return repository.findByTenantIdOrderBySortOrderAscCodeAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public TenantPaymentMethod getOrThrow(Long tenantId, PaymentMethodCode code) {
        ensureDefaultsForTenant(tenantId);
        return repository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não configurado."));
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

