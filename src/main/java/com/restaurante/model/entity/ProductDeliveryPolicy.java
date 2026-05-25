package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierVehicleType;
import com.restaurante.model.enums.PackageSize;
import com.restaurante.model.enums.ProductDeliveryPolicyStatus;
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
@Table(name = "product_delivery_policies", indexes = {
        @Index(name = "uq_product_delivery_policy_key", columnList = "tenant_id, product_id", unique = true),
        @Index(name = "idx_product_delivery_policy_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductDeliveryPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @Column(name = "delivery_eligible", nullable = false)
    private boolean deliveryEligible = false;

    @Column(name = "fragile", nullable = false)
    private boolean fragile = false;

    @Column(name = "requires_cooling", nullable = false)
    private boolean requiresCooling = false;

    @Column(name = "max_delivery_distance_km", precision = 9, scale = 3)
    private BigDecimal maxDeliveryDistanceKm;

    @Column(name = "estimated_package_weight", precision = 9, scale = 3)
    private BigDecimal estimatedPackageWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_size", length = 40)
    private PackageSize packageSize;

    @Column(name = "allow_motorbike_delivery", nullable = false)
    private boolean allowMotorbikeDelivery = true;

    @Column(name = "allow_car_delivery", nullable = false)
    private boolean allowCarDelivery = true;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ProductDeliveryPolicyStatus status = ProductDeliveryPolicyStatus.ACTIVE;
}

