package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierPenaltyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierPenaltyPolicyRepository extends JpaRepository<CourierPenaltyPolicy, Long> {
    Optional<CourierPenaltyPolicy> findFirstByStatus(String status);
}
