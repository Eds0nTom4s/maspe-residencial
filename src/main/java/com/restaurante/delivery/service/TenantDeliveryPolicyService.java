package com.restaurante.delivery.service;

import com.restaurante.delivery.dto.request.UpdateTenantDeliveryPolicyRequest;
import com.restaurante.delivery.dto.response.TenantDeliveryPolicyResponse;
import com.restaurante.delivery.repository.TenantDeliveryPolicyRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantDeliveryPolicy;
import com.restaurante.model.enums.DeliveryCancelAllowedUntilStatus;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantDeliveryPolicyService {

    private final TenantRepository tenantRepository;
    private final TenantDeliveryPolicyRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public TenantDeliveryPolicy getOrNull(Long tenantId) {
        if (tenantId == null) return null;
        return repository.findByTenantId(tenantId).orElse(null);
    }

    @Transactional
    public TenantDeliveryPolicy getOrCreateDefault(Long tenantId) {
        if (tenantId == null) throw new BusinessException("DELIVERY_POLICY_NOT_FOUND");
        TenantDeliveryPolicy existing = repository.findByTenantId(tenantId).orElse(null);
        if (existing != null) return existing;

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
        TenantDeliveryPolicy p = new TenantDeliveryPolicy();
        p.setTenant(tenant);
        p.setDeliveryEnabled(true);
        p.setDeliveryMode(DeliveryMode.CONSUMA_NETWORK);
        p.setAcceptsConsumaNetwork(true);
        p.setAcceptsTenantOwnDelivery(false);
        p.setAllowCustomerPickup(true);
        p.setRequirePaymentBeforeDelivery(true);
        p.setAutoCreateDeliveryJobAfterPayment(false);
        p.setMaxDeliveryDistanceKm(BigDecimal.valueOf(10.0));
        p.setCancelAllowedUntilStatus(DeliveryCancelAllowedUntilStatus.BEFORE_COURIER_ACCEPTED);
        p.setStatus(TenantDeliveryPolicyStatus.ACTIVE);
        return repository.save(p);
    }

    @Transactional
    public TenantDeliveryPolicyResponse update(Long tenantId, UpdateTenantDeliveryPolicyRequest req) {
        TenantDeliveryPolicy p = getOrCreateDefault(tenantId);
        if (p.getStatus() != TenantDeliveryPolicyStatus.ACTIVE) throw new BusinessException("DELIVERY_POLICY_DISABLED");

        if (req != null && req.getMaxDeliveryDistanceKm() != null && req.getMaxDeliveryDistanceKm().compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new BusinessException("DELIVERY_POLICY_NOT_FOUND");
        }

        p.setDeliveryEnabled(req != null && req.isDeliveryEnabled());
        p.setDeliveryMode(req != null && req.getDeliveryMode() != null ? req.getDeliveryMode() : p.getDeliveryMode());
        p.setAcceptsConsumaNetwork(req != null && req.isAcceptsConsumaNetwork());
        p.setAcceptsTenantOwnDelivery(req != null && req.isAcceptsTenantOwnDelivery());
        p.setAllowCustomerPickup(req == null || req.isAllowCustomerPickup());
        p.setRequirePaymentBeforeDelivery(req == null || req.isRequirePaymentBeforeDelivery());
        p.setAutoCreateDeliveryJobAfterPayment(req != null && req.isAutoCreateDeliveryJobAfterPayment());
        p.setMaxDeliveryDistanceKm(req != null ? req.getMaxDeliveryDistanceKm() : null);
        p.setPreparationTimeMinutes(req != null ? req.getPreparationTimeMinutes() : null);
        p.setCancelAllowedUntilStatus(req != null && req.getCancelAllowedUntilStatus() != null ? req.getCancelAllowedUntilStatus() : p.getCancelAllowedUntilStatus());
        p.setDeliveryNotes(req != null ? req.getDeliveryNotes() : null);
        p = repository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_DELIVERY_POLICY_UPDATED,
                OperationalEntityType.TENANT_DELIVERY_POLICY,
                p.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "TenantDeliveryPolicy atualizado",
                Map.of(
                        "tenantId", tenantId,
                        "deliveryEnabled", p.isDeliveryEnabled(),
                        "deliveryMode", p.getDeliveryMode() != null ? p.getDeliveryMode().name() : null
                ),
                null,
                null
        );

        return map(p);
    }

    @Transactional(readOnly = true)
    public TenantDeliveryPolicyResponse getResponse(Long tenantId) {
        TenantDeliveryPolicy p = getOrCreateDefault(tenantId);
        return map(p);
    }

    private static TenantDeliveryPolicyResponse map(TenantDeliveryPolicy p) {
        return new TenantDeliveryPolicyResponse(
                p.getId(),
                p.isDeliveryEnabled(),
                p.getDeliveryMode(),
                p.isAcceptsConsumaNetwork(),
                p.isAcceptsTenantOwnDelivery(),
                p.isAllowCustomerPickup(),
                p.isRequirePaymentBeforeDelivery(),
                p.isAutoCreateDeliveryJobAfterPayment(),
                p.getMaxDeliveryDistanceKm(),
                p.getPreparationTimeMinutes(),
                p.getCancelAllowedUntilStatus(),
                p.getDeliveryNotes(),
                p.getStatus()
        );
    }
}

