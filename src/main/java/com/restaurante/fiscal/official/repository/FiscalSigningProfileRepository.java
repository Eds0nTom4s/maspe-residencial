package com.restaurante.fiscal.official.repository;

import com.restaurante.model.entity.FiscalSigningProfile;
import com.restaurante.model.enums.FiscalSigningProfileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FiscalSigningProfileRepository extends JpaRepository<FiscalSigningProfile, Long> {
    Optional<FiscalSigningProfile> findByTenantIdAndStatus(Long tenantId, FiscalSigningProfileStatus status);

    default Optional<FiscalSigningProfile> findActiveByTenant(Long tenantId, LocalDateTime now) {
        // MVP: sem vigência no schema; usar apenas status ACTIVE
        return findByTenantIdAndStatus(tenantId, FiscalSigningProfileStatus.ACTIVE);
    }
}

