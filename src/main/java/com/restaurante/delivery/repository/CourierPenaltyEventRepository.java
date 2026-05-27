package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierPenaltyEvent;
import com.restaurante.model.enums.CourierPenaltyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CourierPenaltyEventRepository extends JpaRepository<CourierPenaltyEvent, Long> {
    List<CourierPenaltyEvent> findByCourierId(Long courierId);
    List<CourierPenaltyEvent> findByCourierIdAndStatus(Long courierId, CourierPenaltyStatus status);
    List<CourierPenaltyEvent> findByCourierIdAndAppliedAtAfter(Long courierId, LocalDateTime since);
}
