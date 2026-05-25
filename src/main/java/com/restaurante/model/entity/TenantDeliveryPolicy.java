package com.restaurante.model.entity;

import com.restaurante.model.enums.DeliveryCancelAllowedUntilStatus;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
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

import java.math.BigDecimal;

@Entity
@Table(name = "tenant_delivery_policies", indexes = {
        @Index(name = "uq_tenant_delivery_policy_tenant", columnList = "tenant_id", unique = true),
        @Index(name = "idx_tenant_delivery_policy_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantDeliveryPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "delivery_enabled", nullable = false)
    private boolean deliveryEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 40)
    private DeliveryMode deliveryMode = DeliveryMode.PICKUP_ONLY;

    @Column(name = "accepts_consuma_network", nullable = false)
    private boolean acceptsConsumaNetwork = false;

    @Column(name = "accepts_tenant_own_delivery", nullable = false)
    private boolean acceptsTenantOwnDelivery = false;

    @Column(name = "allow_customer_pickup", nullable = false)
    private boolean allowCustomerPickup = true;

    @Column(name = "require_payment_before_delivery", nullable = false)
    private boolean requirePaymentBeforeDelivery = true;

    @Column(name = "auto_create_delivery_job_after_payment", nullable = false)
    private boolean autoCreateDeliveryJobAfterPayment = false;

    @Column(name = "max_delivery_distance_km", precision = 9, scale = 3)
    private BigDecimal maxDeliveryDistanceKm;

    @Column(name = "preparation_time_minutes")
    private Integer preparationTimeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_allowed_until_status", nullable = false, length = 60)
    private DeliveryCancelAllowedUntilStatus cancelAllowedUntilStatus = DeliveryCancelAllowedUntilStatus.BEFORE_COURIER_ACCEPTED;

    @Column(name = "delivery_notes", columnDefinition = "text")
    private String deliveryNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TenantDeliveryPolicyStatus status = TenantDeliveryPolicyStatus.ACTIVE;
}

