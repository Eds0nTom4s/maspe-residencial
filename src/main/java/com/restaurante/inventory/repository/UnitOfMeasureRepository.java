package com.restaurante.inventory.repository;

import com.restaurante.model.entity.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {
    Optional<UnitOfMeasure> findByTenantIdAndCode(Long tenantId, String code);

    List<UnitOfMeasure> findAllByTenantIdOrTenantIsNullOrderByCodeAsc(Long tenantId);
}

