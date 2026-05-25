package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.UnitConversionRepository;
import com.restaurante.inventory.repository.UnitOfMeasureRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnitConversion;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.UnitConversionStatus;
import com.restaurante.model.enums.UnitOfMeasureStatus;
import com.restaurante.model.enums.UnitOfMeasureType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryUnitService {

    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final UnitConversionRepository unitConversionRepository;

    @Transactional(readOnly = true)
    public List<UnitOfMeasure> listUnits(Long tenantId) {
        return unitOfMeasureRepository.findAllByTenantIdOrTenantIsNullOrderByCodeAsc(tenantId);
    }

    @Transactional
    public UnitOfMeasure createUnit(Tenant tenant, String code, String name, UnitOfMeasureType type, boolean decimalAllowed) {
        if (code == null || code.isBlank()) throw new BusinessException("INVENTORY_UNIT_CODE_REQUIRED");
        if (name == null || name.isBlank()) throw new BusinessException("INVENTORY_UNIT_NAME_REQUIRED");
        UnitOfMeasure u = new UnitOfMeasure();
        u.setTenant(tenant);
        u.setCode(code);
        u.setName(name);
        u.setType(type != null ? type : UnitOfMeasureType.OTHER);
        u.setDecimalAllowed(decimalAllowed);
        u.setStatus(UnitOfMeasureStatus.ACTIVE);
        return unitOfMeasureRepository.save(u);
    }

    @Transactional
    public UnitConversion createConversion(Long tenantId, Long fromUnitId, Long toUnitId, BigDecimal factor) {
        if (factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("INVENTORY_UNIT_CONVERSION_INVALID_FACTOR");
        UnitOfMeasure from = unitOfMeasureRepository.findById(fromUnitId).orElseThrow(() -> new BusinessException("INVENTORY_UNIT_NOT_FOUND"));
        UnitOfMeasure to = unitOfMeasureRepository.findById(toUnitId).orElseThrow(() -> new BusinessException("INVENTORY_UNIT_NOT_FOUND"));
        UnitConversion c = new UnitConversion();
        c.setTenant(from.getTenant() != null ? from.getTenant() : null);
        if (tenantId != null && from.getTenant() != null && !from.getTenant().getId().equals(tenantId)) throw new BusinessException("INVENTORY_FORBIDDEN");
        c.setFromUnit(from);
        c.setToUnit(to);
        c.setFactor(factor);
        c.setStatus(UnitConversionStatus.ACTIVE);
        return unitConversionRepository.save(c);
    }
}

