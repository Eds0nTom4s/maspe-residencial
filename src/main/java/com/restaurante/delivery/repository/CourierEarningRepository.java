package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierEarning;
import com.restaurante.model.enums.CourierEarningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierEarningRepository extends JpaRepository<CourierEarning, Long> {
    Optional<CourierEarning> findByDeliveryJobId(Long deliveryJobId);
    List<CourierEarning> findByCourierId(Long courierId);
    List<CourierEarning> findByCourierIdAndStatus(Long courierId, CourierEarningStatus status);
}
