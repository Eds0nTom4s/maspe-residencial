package com.restaurante.repository;

import com.restaurante.model.entity.LegacyProvisioningUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyProvisioningUsageEventRepository extends JpaRepository<LegacyProvisioningUsageEvent, Long> {
}
