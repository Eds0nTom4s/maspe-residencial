package com.restaurante.inventory.repository;

import com.restaurante.model.entity.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {
    Optional<UnitConversion> findByTenantIdAndFromUnitIdAndToUnitId(Long tenantId, Long fromUnitId, Long toUnitId);

    Optional<UnitConversion> findByTenantIsNullAndFromUnitIdAndToUnitId(Long fromUnitId, Long toUnitId);
}

