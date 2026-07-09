package com.restaurante.repository;

import com.restaurante.model.entity.BusinessAccountLimitOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessAccountLimitOverrideRepository extends JpaRepository<BusinessAccountLimitOverride, Long> {

    Optional<BusinessAccountLimitOverride> findByBusinessAccountIdAndAtivoTrue(Long businessAccountId);

    Optional<BusinessAccountLimitOverride> findByBusinessAccountId(Long businessAccountId);
}
