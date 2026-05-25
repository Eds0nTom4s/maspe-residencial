package com.restaurante.billing.repository;

import com.restaurante.model.entity.UsageMetric;
import com.restaurante.model.enums.UsageMetricCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageMetricRepository extends JpaRepository<UsageMetric, Long> {
    Optional<UsageMetric> findByCode(UsageMetricCode code);
}

