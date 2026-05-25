package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierLocationSource;
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
@Table(name = "courier_location_pings", indexes = {
        @Index(name = "idx_courier_ping_courier_time", columnList = "courier_id, recorded_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierLocationPing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 9, scale = 3)
    private BigDecimal accuracyMeters;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private CourierLocationSource source = CourierLocationSource.COURIER_APP;
}

