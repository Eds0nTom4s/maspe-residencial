package com.restaurante.billing.service;

import com.restaurante.billing.dto.TenantBillingAccessDecision;
import com.restaurante.billing.enums.TenantBillingOperationType;
import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantAccessBillingGuardService {

    private final TenantBillingCollectionService collectionService;

    public TenantBillingAccessDecision evaluateAccess(Long tenantId, TenantBillingOperationType op) {
        TenantBillingCollectionPolicy policy = collectionService.getEffectivePolicy(tenantId);
        TenantBillingCollectionStatus status = collectionService.evaluateTenantBillingStatus(tenantId, LocalDateTime.now());

        TenantBillingAccessDecision out = new TenantBillingAccessDecision();
        out.setCollectionStatus(status);

        // MVP rule: never block CONFIRM_PAYMENT (customer final payment flow) here.
        if (op == TenantBillingOperationType.CONFIRM_PAYMENT) {
            out.setAllowed(true);
            out.setWarningOnly(status != TenantBillingCollectionStatus.CURRENT && status != TenantBillingCollectionStatus.CLEARED);
            out.setMessageCode(codeFor(status));
            return out;
        }

        if (status == TenantBillingCollectionStatus.SUSPENDED) {
            if (policy.getSuspensionMode() == TenantBillingSuspensionMode.WARNING_ONLY || policy.isAllowOperationWhenSuspended()) {
                out.setAllowed(true);
                out.setWarningOnly(true);
            } else {
                boolean blocked = switch (op) {
                    case ADD_DEVICE -> policy.isRestrictNewDevices();
                    case CREATE_ORDER -> policy.isRestrictNewOrders();
                    case ACCESS_ADMIN -> policy.isRestrictAdminAccess();
                    default -> false;
                };
                out.setAllowed(!blocked);
                out.setWarningOnly(!blocked);
                if (blocked) {
                    out.setBlockReason("TENANT_BILLING_ACCESS_RESTRICTED");
                }
            }
            out.setMessageCode(codeFor(status));
            return out;
        }

        if (status == TenantBillingCollectionStatus.OVERDUE || status == TenantBillingCollectionStatus.IN_GRACE_PERIOD || status == TenantBillingCollectionStatus.SUSPENSION_WARNING) {
            out.setAllowed(true);
            out.setWarningOnly(true);
            out.setMessageCode(codeFor(status));
            return out;
        }

        out.setAllowed(true);
        out.setWarningOnly(false);
        out.setMessageCode(codeFor(status));
        return out;
    }

    private String codeFor(TenantBillingCollectionStatus status) {
        if (status == null) return "BILLING_CURRENT";
        return switch (status) {
            case CURRENT, CLEARED -> "BILLING_CURRENT";
            case DUE_SOON -> "BILLING_DUE_SOON";
            case OVERDUE -> "BILLING_OVERDUE_WARNING";
            case IN_GRACE_PERIOD -> "BILLING_IN_GRACE_PERIOD";
            case SUSPENSION_WARNING -> "BILLING_SUSPENSION_WARNING";
            case SUSPENDED -> "BILLING_SUSPENDED_OPERATION_RESTRICTED";
        };
    }
}

