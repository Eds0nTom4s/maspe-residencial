package com.restaurante.repository;

import com.restaurante.model.entity.OnboardingCommandRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingCommandRecordRepository extends JpaRepository<OnboardingCommandRecord, Long> {
    Optional<OnboardingCommandRecord> findByScopeKeyAndActionAndIdempotencyKey(
            String scopeKey, String action, String idempotencyKey);
}
