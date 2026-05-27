package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierSettlementLineStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "courier_settlement_lines")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierSettlementLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private CourierSettlementBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_earning_id", nullable = false)
    private CourierEarning courierEarning;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_job_id", nullable = false)
    private DeliveryJob deliveryJob;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CourierSettlementLineStatus status = CourierSettlementLineStatus.PENDING;
}
