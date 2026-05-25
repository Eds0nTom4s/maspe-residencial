package com.restaurante.billing.service;

import com.restaurante.billing.repository.BillingCycleRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.BillingCycleStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final BillingCycleRepository cycleRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public BillingCycle getOrOpenCurrentCycle(TenantSubscription sub, LocalDateTime at) {
        if (sub == null || sub.getTenant() == null || sub.getId() == null) throw new BusinessException("BILLING_CYCLE_NOT_FOUND");
        Tenant tenant = sub.getTenant();

        LocalDateTime now = at != null ? at : LocalDateTime.now();
        BillingCycle current = cycleRepository.findCurrent(
                tenant.getId(),
                sub.getId(),
                Set.of(BillingCycleStatus.OPEN, BillingCycleStatus.USAGE_FINALIZED, BillingCycleStatus.INVOICED),
                now
        ).orElse(null);
        if (current != null) return current;

        int anchor = sub.getBillingAnchorDay() != null ? Math.max(1, Math.min(28, sub.getBillingAnchorDay())) : 1;
        LocalDate startDate = LocalDate.of(now.getYear(), now.getMonth(), anchor);
        if (now.toLocalDate().isBefore(startDate)) startDate = startDate.minusMonths(1);
        LocalDateTime periodStart = startDate.atTime(LocalTime.MIDNIGHT);
        LocalDateTime periodEnd = periodStart.plusMonths(1);

        BillingCycle created = new BillingCycle();
        created.setTenant(tenant);
        created.setSubscription(sub);
        created.setPeriodStart(periodStart);
        created.setPeriodEnd(periodEnd);
        created.setStatus(BillingCycleStatus.OPEN);
        created = cycleRepository.save(created);

        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.BILLING_CYCLE_OPENED,
                OperationalEntityType.BILLING_CYCLE,
                created.getId(),
                OperationalOrigem.SYSTEM,
                "Billing cycle aberto",
                Map.of(
                        "tenantId", tenant.getId(),
                        "subscriptionId", sub.getId(),
                        "cycleId", created.getId(),
                        "periodStart", created.getPeriodStart(),
                        "periodEnd", created.getPeriodEnd()
                ),
                null,
                null
        );

        return created;
    }

    @Transactional
    public BillingCycle finalizeUsage(Long tenantId, Long cycleId) {
        if (tenantId == null || cycleId == null) throw new BusinessException("BILLING_CYCLE_NOT_FOUND");
        BillingCycle cycle = cycleRepository.findByTenantIdAndId(tenantId, cycleId).orElseThrow(() -> new BusinessException("BILLING_CYCLE_NOT_FOUND"));
        if (cycle.getStatus() != BillingCycleStatus.OPEN) throw new BusinessException("BILLING_CYCLE_INVALID_STATE");
        cycle.setStatus(BillingCycleStatus.USAGE_FINALIZED);
        cycle.setUsageFinalizedAt(LocalDateTime.now());
        cycle = cycleRepository.save(cycle);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.BILLING_CYCLE_USAGE_FINALIZED,
                OperationalEntityType.BILLING_CYCLE,
                cycle.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Uso do ciclo finalizado",
                Map.of("tenantId", tenantId, "cycleId", cycle.getId()),
                null,
                null
        );
        return cycle;
    }
}
