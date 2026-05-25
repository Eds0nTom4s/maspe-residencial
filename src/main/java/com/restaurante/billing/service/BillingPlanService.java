package com.restaurante.billing.service;

import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.enums.BillingPlanStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingPlanService {

    private final TenantGuard tenantGuard;
    private final BillingPlanRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public BillingPlan create(BillingPlan plan) {
        tenantGuard.assertPlatformAdmin();
        if (plan == null) throw new BusinessException("BILLING_PLAN_NOT_FOUND");
        if (plan.getCode() == null || plan.getCode().isBlank()) throw new BusinessException("BILLING_PLAN_NOT_FOUND");
        if (repository.findByCode(plan.getCode()).isPresent()) throw new BusinessException("TENANT_BILLING_INVOICE_ALREADY_EXISTS");
        plan.setStatus(plan.getStatus() != null ? plan.getStatus() : BillingPlanStatus.ACTIVE);
        BillingPlan saved = repository.save(plan);
        TenantContextHolder.get().map(c -> c.tenantId()).ifPresent(tid ->
                operationalEventLogService.logGenericForTenant(
                        tid,
                        OperationalEventType.BILLING_PLAN_CREATED,
                        OperationalEntityType.BILLING_PLAN,
                        saved.getId(),
                        OperationalOrigem.SYSTEM,
                        "BillingPlan criado",
                        Map.of("tenantId", tid, "planId", saved.getId(), "code", saved.getCode(), "status", saved.getStatus().name()),
                        null,
                        null
                )
        );
        return saved;
    }

    @Transactional
    public BillingPlan update(Long planId, BillingPlan patch) {
        tenantGuard.assertPlatformAdmin();
        BillingPlan plan = repository.findById(planId).orElseThrow(() -> new BusinessException("BILLING_PLAN_NOT_FOUND"));
        if (patch.getName() != null) plan.setName(patch.getName());
        if (patch.getDescription() != null) plan.setDescription(patch.getDescription());
        if (patch.getStatus() != null) plan.setStatus(patch.getStatus());
        if (patch.getBillingInterval() != null) plan.setBillingInterval(patch.getBillingInterval());
        if (patch.getCurrency() != null) plan.setCurrency(patch.getCurrency());
        if (patch.getBasePrice() != null) plan.setBasePrice(patch.getBasePrice());
        if (patch.getIncludedTransactions() != null) plan.setIncludedTransactions(patch.getIncludedTransactions());
        if (patch.getOveragePricePerTransaction() != null) plan.setOveragePricePerTransaction(patch.getOveragePricePerTransaction());
        if (patch.getTransactionFeePercentage() != null) plan.setTransactionFeePercentage(patch.getTransactionFeePercentage());
        if (patch.getMinimumMonthlyFee() != null) plan.setMinimumMonthlyFee(patch.getMinimumMonthlyFee());
        BillingPlan saved = repository.save(plan);
        TenantContextHolder.get().map(c -> c.tenantId()).ifPresent(tid ->
                operationalEventLogService.logGenericForTenant(
                        tid,
                        OperationalEventType.BILLING_PLAN_UPDATED,
                        OperationalEntityType.BILLING_PLAN,
                        saved.getId(),
                        OperationalOrigem.SYSTEM,
                        "BillingPlan atualizado",
                        Map.of("tenantId", tid, "planId", saved.getId(), "code", saved.getCode(), "status", saved.getStatus().name()),
                        null,
                        null
                )
        );
        return saved;
    }
}
