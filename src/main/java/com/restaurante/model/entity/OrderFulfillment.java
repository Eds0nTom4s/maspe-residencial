package com.restaurante.model.entity;

import com.restaurante.model.enums.FulfillmentType;
import com.restaurante.model.enums.OrderFulfillmentStatus;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "order_fulfillments", indexes = {
        @Index(name = "uq_order_fulfillment_pedido", columnList = "tenant_id, pedido_id", unique = true),
        @Index(name = "idx_order_fulfillment_status", columnList = "tenant_id, status"),
        @Index(name = "idx_order_fulfillment_type", columnList = "tenant_id, fulfillment_type")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrderFulfillment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 60)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 60)
    private OrderFulfillmentStatus status = OrderFulfillmentStatus.DRAFT;

    @Column(name = "customer_name", length = 160)
    private String customerName;

    @Column(name = "customer_phone_masked", length = 40)
    private String customerPhoneMasked;

    @Column(name = "delivery_address_text", columnDefinition = "text")
    private String deliveryAddressText;

    @Column(name = "delivery_latitude", precision = 10, scale = 6)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 6)
    private BigDecimal deliveryLongitude;

    @Column(name = "delivery_notes", columnDefinition = "text")
    private String deliveryNotes;

    @Column(name = "delivery_fee_amount", precision = 19, scale = 2)
    private BigDecimal deliveryFeeAmount;

    @Column(name = "delivery_distance_km", precision = 9, scale = 3)
    private BigDecimal deliveryDistanceKm;

    @Column(name = "delivery_requested_at")
    private LocalDateTime deliveryRequestedAt;

    @Column(name = "pickup_ready_at")
    private LocalDateTime pickupReadyAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;
}

