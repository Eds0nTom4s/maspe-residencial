package com.restaurante.billing.service;

import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantSubscriptionService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final BillingPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public TenantSubscription getCurrentForTenant(Long tenantId) {
        if (tenantId == null) throw new BusinessException("TENANT_SUBSCRIPTION_NOT_FOUND");
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return subscriptionRepository.findTopByTenantIdOrderByIdDesc(tenantId).orElse(null);
    }

    @Transactional
    public TenantSubscription createOrReplaceForTenant(Long tenantId, Long planId, TenantSubscriptionStatus status, Integer anchorDay) {
        tenantGuard.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_SUBSCRIPTION_NOT_FOUND"));
        BillingPlan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException("BILLING_PLAN_NOT_FOUND"));

        int day = anchorDay != null ? Math.max(1, Math.min(28, anchorDay)) : 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDate startDate = LocalDate.of(now.getYear(), now.getMonth(), day);
        if (now.toLocalDate().isBefore(startDate)) {
            startDate = startDate.minusMonths(1);
        }
        LocalDateTime periodStart = startDate.atTime(LocalTime.MIDNIGHT);
        LocalDateTime periodEnd = periodStart.plusMonths(1);

        TenantSubscription sub = new TenantSubscription();
        sub.setTenant(tenant);
        sub.setBillingPlan(plan);
        sub.setStatus(status != null ? status : TenantSubscriptionStatus.TRIALING);
        sub.setStartedAt(now);
        sub.setCurrentPeriodStart(periodStart);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setBillingAnchorDay(day);
        sub.setCurrency(plan.getCurrency());
        sub.setAutoRenew(true);
        sub = subscriptionRepository.save(sub);

        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.TENANT_SUBSCRIPTION_CREATED,
                OperationalEntityType.TENANT_SUBSCRIPTION,
                sub.getId(),
                OperationalOrigem.SYSTEM,
                "Subscription criada",
                Map.of(
                        "tenantId", tenant.getId(),
                        "subscriptionId", sub.getId(),
                        "planId", plan.getId(),
                        "status", sub.getStatus().name(),
                        "periodStart", sub.getCurrentPeriodStart(),
                        "periodEnd", sub.getCurrentPeriodEnd()
                ),
                null,
                null
        );

        return sub;
    }

    @Transactional
    public TenantSubscription updateForTenant(Long tenantId, Long subscriptionId, TenantSubscriptionStatus status) {
        tenantGuard.assertPlatformAdmin();
        TenantSubscription sub = subscriptionRepository.findByTenantIdAndId(tenantId, subscriptionId)
                .orElseThrow(() -> new BusinessException("TENANT_SUBSCRIPTION_NOT_FOUND"));
        TenantSubscriptionStatus before = sub.getStatus();
        if (status != null) sub.setStatus(status);
        sub = subscriptionRepository.save(sub);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_SUBSCRIPTION_UPDATED,
                OperationalEntityType.TENANT_SUBSCRIPTION,
                sub.getId(),
                OperationalOrigem.SYSTEM,
                "Subscription atualizada",
                Map.of(
                        "tenantId", tenantId,
                        "subscriptionId", sub.getId(),
                        "before", before != null ? before.name() : null,
                        "after", sub.getStatus() != null ? sub.getStatus().name() : null
                ),
                null,
                null
        );

        return sub;
    }
}

