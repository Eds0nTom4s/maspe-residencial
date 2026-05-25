package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.UnitConversionRepository;
import com.restaurante.model.entity.UnitConversion;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.UnitConversionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private final UnitConversionRepository unitConversionRepository;

    @Transactional(readOnly = true)
    public BigDecimal convertQuantity(Long tenantId,
                                     BigDecimal quantity,
                                     UnitOfMeasure fromUnit,
                                     UnitOfMeasure toUnit,
                                     int calculationScale,
                                     RoundingMode roundingMode) {
        if (quantity == null) return null;
        if (fromUnit == null || toUnit == null) {
            throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        }
        if (fromUnit.getId().equals(toUnit.getId())) {
            return quantity;
        }

        UnitConversion conversion = unitConversionRepository
                .findByTenantIdAndFromUnitIdAndToUnitId(tenantId, fromUnit.getId(), toUnit.getId())
                .orElseGet(() -> unitConversionRepository
                        .findByTenantIsNullAndFromUnitIdAndToUnitId(fromUnit.getId(), toUnit.getId())
                        .orElse(null));

        if (conversion == null || conversion.getStatus() != UnitConversionStatus.ACTIVE) {
            throw new BusinessException("INVENTORY_UNIT_CONVERSION_NOT_FOUND");
        }

        return quantity.multiply(conversion.getFactor()).setScale(calculationScale, roundingMode);
    }
}

