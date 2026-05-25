package com.restaurante.billing.service;

import com.restaurante.billing.repository.UsageAdjustmentRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UsageAdjustment;
import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.UsageAdjustmentType;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static com.restaurante.billing.util.BillingMath.scaleMoney;
import static com.restaurante.billing.util.BillingMath.scaleQty;

@Service
@RequiredArgsConstructor
public class UsageAdjustmentService {

    private final TenantRepository tenantRepository;
    private final UsageAdjustmentRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public UsageAdjustment create(Long tenantId,
                                  UsageMetricCode metricCode,
                                  UsageAdjustmentType type,
                                  BigDecimal quantityDelta,
                                  BigDecimal amountDelta,
                                  String reason,
                                  String referenceType,
                                  Long referenceId,
                                  UsageEvent originalEvent) {
        if (tenantId == null) throw new BusinessException("USAGE_ADJUSTMENT_INVALID");
        if (metricCode == null || type == null) throw new BusinessException("USAGE_ADJUSTMENT_INVALID");

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("USAGE_ADJUSTMENT_INVALID"));

        UsageAdjustment a = new UsageAdjustment();
        a.setTenant(tenant);
        a.setMetricCode(metricCode);
        a.setAdjustmentType(type);
        a.setQuantityDelta(scaleQty(quantityDelta));
        a.setAmountDelta(scaleMoney(amountDelta));
        a.setReason(reason);
        a.setReferenceType(referenceType);
        a.setReferenceId(referenceId);
        a.setOriginalUsageEvent(originalEvent);
        a = repository.save(a);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.USAGE_ADJUSTMENT_CREATED,
                OperationalEntityType.USAGE_ADJUSTMENT,
                a.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Usage adjustment criado",
                Map.of(
                        "tenantId", tenantId,
                        "metricCode", metricCode.name(),
                        "type", type.name(),
                        "quantityDelta", a.getQuantityDelta(),
                        "amountDelta", a.getAmountDelta()
                ),
                null,
                null
        );

        return a;
    }
}
