package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierSettlementBatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courier_settlement_batches")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierSettlementBatch extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true, length = 100)
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CourierSettlementBatchStatus status = CourierSettlementBatchStatus.DRAFT;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Column(name = "total_earnings_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarningsAmount = BigDecimal.ZERO;

    @Column(name = "total_commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCommissionAmount = BigDecimal.ZERO;

    @Column(name = "total_jobs", nullable = false)
    private Integer totalJobs = 0;
}
