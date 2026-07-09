package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierProfile;
import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierStatus;
import com.restaurante.model.enums.CourierVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {
    Optional<CourierProfile> findByCourierUser_Id(Long userId);
    Optional<CourierProfile> findById(Long id);
    Optional<CourierProfile> findByCourierCode(String courierCode);

    @Query("""
            select c from CourierProfile c
            where c.status = :status
              and c.verificationStatus = :verificationStatus
              and c.currentAvailability = :availability
              and (c.activeDeliveryJobId is null)
              and c.lastLocationUpdateAt is not null
              and c.lastLocationUpdateAt >= :minLocationTime
            """)
    List<CourierProfile> findAvailableNearbyCandidates(CourierStatus status,
                                                      CourierVerificationStatus verificationStatus,
                                                      CourierAvailability availability,
                                                      LocalDateTime minLocationTime);
}

