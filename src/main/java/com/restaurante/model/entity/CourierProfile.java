package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierStatus;
import com.restaurante.model.enums.CourierVehicleType;
import com.restaurante.model.enums.CourierVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courier_profiles", indexes = {
        @Index(name = "uq_courier_code", columnList = "courier_code", unique = true),
        @Index(name = "uq_courier_user", columnList = "courier_user_id", unique = true),
        @Index(name = "idx_courier_status", columnList = "status, verification_status, current_availability"),
        @Index(name = "idx_courier_location_time", columnList = "last_location_update_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_user_id")
    private User courierUser;

    @Column(name = "courier_code", nullable = false, length = 40)
    private String courierCode;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "phone_masked", length = 40)
    private String phoneMasked;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CourierStatus status = CourierStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 40)
    private CourierVerificationStatus verificationStatus = CourierVerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 40)
    private CourierVehicleType vehicleType = CourierVehicleType.MOTORBIKE;

    @Column(name = "vehicle_plate_masked", length = 60)
    private String vehiclePlateMasked;

    @Column(name = "has_own_vehicle", nullable = false)
    private boolean hasOwnVehicle = true;

    @Column(name = "accepts_terms", nullable = false)
    private boolean acceptsTerms = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_availability", nullable = false, length = 40)
    private CourierAvailability currentAvailability = CourierAvailability.OFFLINE;

    @Column(name = "current_latitude", precision = 10, scale = 6)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 6)
    private BigDecimal currentLongitude;

    @Column(name = "last_location_update_at")
    private LocalDateTime lastLocationUpdateAt;

    @Column(name = "active_delivery_job_id")
    private Long activeDeliveryJobId;
}

