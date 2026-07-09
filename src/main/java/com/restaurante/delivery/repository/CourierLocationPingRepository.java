package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierLocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourierLocationPingRepository extends JpaRepository<CourierLocationPing, Long> {
    List<CourierLocationPing> findByCourier_IdOrderByRecordedAtDesc(Long courierId);
}

