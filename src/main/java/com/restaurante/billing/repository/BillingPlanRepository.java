package com.restaurante.billing.repository;

import com.restaurante.model.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {
    Optional<BillingPlan> findByCode(String code);
}

