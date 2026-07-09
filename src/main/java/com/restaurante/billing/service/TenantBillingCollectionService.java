package com.restaurante.billing.service;

import com.restaurante.billing.repository.TenantBillingCollectionPolicyRepository;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantBillingCollectionPolicyStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;

@Service
@RequiredArgsConstructor
public class TenantBillingCollectionService {

    private static final EnumSet<TenantBillingInvoiceStatus> UNPAID_STATUSES = EnumSet.of(
            TenantBillingInvoiceStatus.ISSUED,
            TenantBillingInvoiceStatus.PARTIALLY_PAID,
            TenantBillingInvoiceStatus.OVERDUE
    );

    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingCollectionPolicyRepository policyRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public TenantBillingCollectionPolicy getEffectivePolicy(Long tenantId) {
        if (tenantId == null) throw new BusinessException("TENANT_BILLING_COLLECTION_POLICY_NOT_FOUND");
        TenantBillingCollectionPolicy policy = policyRepository.findByTenantId(tenantId).orElse(null);
        if (policy == null) {
            policy = new TenantBillingCollectionPolicy();
            policy.setGracePeriodDays(7);
            policy.setOverdueWarningDays(3);
            policy.setAutoMarkOverdue(true);
            policy.setAllowOperationWhenOverdue(true);
            policy.setAllowOperationWhenSuspended(false);
            policy.setSuspensionMode(com.restaurante.model.enums.TenantBillingSuspensionMode.WARNING_ONLY);
            policy.setSuspensionAfterDays(15);
            policy.setRestrictNewOrders(false);
            policy.setRestrictNewDevices(true);
            policy.setRestrictAdminAccess(false);
            policy.setStatus(TenantBillingCollectionPolicyStatus.ACTIVE);
        }
        return policy;
    }

    @Transactional
    public TenantBillingCollectionStatus evaluateTenantBillingStatus(Long tenantId, LocalDateTime now) {
        LocalDateTime at = now != null ? now : LocalDateTime.now();
        TenantBillingCollectionPolicy policy = getEffectivePolicy(tenantId);

        if (policy.getStatus() != TenantBillingCollectionPolicyStatus.ACTIVE) {
            return TenantBillingCollectionStatus.CURRENT;
        }

        List<TenantBillingInvoice> overdueCandidates = invoiceRepository.findOverdueCandidates(tenantId, UNPAID_STATUSES, at);
        if (!overdueCandidates.isEmpty() && policy.isAutoMarkOverdue()) {
            for (TenantBillingInvoice inv : overdueCandidates) {
                markInvoiceOverdueInternal(tenantId, inv, at, policy);
            }
        }

        // Re-read after updates to compute effective status
        overdueCandidates = invoiceRepository.findOverdueCandidates(tenantId, UNPAID_STATUSES, at);
        if (overdueCandidates.isEmpty()) {
            return TenantBillingCollectionStatus.CURRENT;
        }

        TenantBillingInvoice oldest = overdueCandidates.get(0);
        LocalDateTime overdueAt = oldest.getOverdueAt() != null ? oldest.getOverdueAt() : oldest.getDueAt();
        if (overdueAt == null) overdueAt = at;
        LocalDateTime graceEnds = oldest.getGracePeriodEndsAt();

        if (graceEnds != null && at.isBefore(graceEnds)) {
            return TenantBillingCollectionStatus.IN_GRACE_PERIOD;
        }

        long daysOverdue = java.time.Duration.between(overdueAt, at).toDays();
        if (daysOverdue >= nzInt(policy.getSuspensionAfterDays())) {
            if (policy.getSuspensionMode() == com.restaurante.model.enums.TenantBillingSuspensionMode.SOFT_SUSPENSION
                    || policy.getSuspensionMode() == com.restaurante.model.enums.TenantBillingSuspensionMode.HARD_SUSPENSION) {
                return TenantBillingCollectionStatus.SUSPENDED;
            }
            return TenantBillingCollectionStatus.SUSPENSION_WARNING;
        }

        return TenantBillingCollectionStatus.OVERDUE;
    }

    @Transactional
    public TenantBillingInvoice markInvoiceOverdue(Long tenantId, Long invoiceId) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
        return markInvoiceOverdueInternal(tenantId, inv, LocalDateTime.now(), getEffectivePolicy(tenantId));
    }

    private TenantBillingInvoice markInvoiceOverdueInternal(Long tenantId,
                                                            TenantBillingInvoice inv,
                                                            LocalDateTime at,
                                                            TenantBillingCollectionPolicy policy) {
        if (inv.getStatus() == TenantBillingInvoiceStatus.PAID || inv.getStatus() == TenantBillingInvoiceStatus.CANCELLED || inv.getStatus() == TenantBillingInvoiceStatus.VOIDED) {
            return inv;
        }
        if (inv.getDueAt() == null || !inv.getDueAt().isBefore(at)) {
            return inv;
        }
        if (scaleMoney(nz(inv.getOutstandingAmount())).compareTo(BigDecimal.ZERO) <= 0) {
            return inv;
        }

        boolean changed = false;
        if (inv.getStatus() != TenantBillingInvoiceStatus.OVERDUE) {
            inv.setStatus(TenantBillingInvoiceStatus.OVERDUE);
            changed = true;
        }
        if (inv.getOverdueAt() == null) {
            inv.setOverdueAt(at);
            changed = true;
        }
        if (inv.getGracePeriodEndsAt() == null) {
            inv.setGracePeriodEndsAt(at.plusDays(nzInt(policy.getGracePeriodDays())));
            changed = true;
        }
        if (inv.getCollectionStatus() != TenantBillingCollectionStatus.OVERDUE) {
            inv.setCollectionStatus(TenantBillingCollectionStatus.OVERDUE);
            changed = true;
        }
        if (!changed) return inv;

        TenantBillingInvoice saved = invoiceRepository.save(inv);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_INVOICE_OVERDUE,
                OperationalEntityType.TENANT_BILLING_INVOICE,
                saved.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Invoice vencida (overdue)",
                Map.of(
                        "tenantId", tenantId,
                        "invoiceId", saved.getId(),
                        "invoiceNumber", saved.getInvoiceNumber(),
                        "dueAt", saved.getDueAt(),
                        "overdueAt", saved.getOverdueAt(),
                        "gracePeriodEndsAt", saved.getGracePeriodEndsAt()
                ),
                null,
                null
        );
        return saved;
    }

    private int nzInt(Integer v) {
        return v == null ? 0 : v;
    }
}
