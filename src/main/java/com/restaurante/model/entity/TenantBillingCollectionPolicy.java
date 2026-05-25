package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantBillingCollectionPolicyStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_billing_collection_policies", indexes = {
        @Index(name = "uq_tenant_billing_collection_policies", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantBillingCollectionPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays = 7;

    @Column(name = "overdue_warning_days", nullable = false)
    private Integer overdueWarningDays = 3;

    @Column(name = "auto_mark_overdue", nullable = false)
    private boolean autoMarkOverdue = true;

    @Column(name = "allow_operation_when_overdue", nullable = false)
    private boolean allowOperationWhenOverdue = true;

    @Column(name = "allow_operation_when_suspended", nullable = false)
    private boolean allowOperationWhenSuspended = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_mode", nullable = false, length = 40)
    private TenantBillingSuspensionMode suspensionMode = TenantBillingSuspensionMode.WARNING_ONLY;

    @Column(name = "suspension_after_days", nullable = false)
    private Integer suspensionAfterDays = 15;

    @Column(name = "restrict_new_orders", nullable = false)
    private boolean restrictNewOrders = false;

    @Column(name = "restrict_new_devices", nullable = false)
    private boolean restrictNewDevices = true;

    @Column(name = "restrict_admin_access", nullable = false)
    private boolean restrictAdminAccess = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantBillingCollectionPolicyStatus status = TenantBillingCollectionPolicyStatus.ACTIVE;
}

