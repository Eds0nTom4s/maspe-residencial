package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierReliabilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierReliabilityProfileRepository extends JpaRepository<CourierReliabilityProfile, Long> {
    Optional<CourierReliabilityProfile> findByCourierId(Long courierId);
}
