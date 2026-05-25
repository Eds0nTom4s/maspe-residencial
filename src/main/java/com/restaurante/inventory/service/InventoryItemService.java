package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.UnitOfMeasureRepository;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.InventoryItemStatus;
import com.restaurante.model.enums.InventoryItemType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public Page<InventoryItem> list(Long tenantId, InventoryItemStatus status, Pageable pageable) {
        return inventoryItemRepository.listByTenant(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public InventoryItem get(Long tenantId, Long itemId) {
        InventoryItem item = inventoryItemRepository.findById(itemId).orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        if (item.getTenant() == null || !item.getTenant().getId().equals(tenantId)) throw new BusinessException("INVENTORY_FORBIDDEN");
        return item;
    }

    @Transactional
    public InventoryItem create(Tenant tenant,
                                String name,
                                String sku,
                                InventoryItemType type,
                                String category,
                                String baseUnitCode,
                                Boolean stockControlEnabled,
                                Boolean allowNegativeStock,
                                BigDecimal minimumQuantity,
                                BigDecimal reorderQuantity) {
        UnitOfMeasure baseUnit = resolveUnit(tenant.getId(), baseUnitCode);
        InventoryItem item = new InventoryItem();
        item.setTenant(tenant);
        item.setName(name);
        item.setSku(sku);
        item.setType(type);
        item.setCategory(category);
        item.setBaseUnit(baseUnit);
        if (stockControlEnabled != null) item.setStockControlEnabled(stockControlEnabled);
        if (allowNegativeStock != null) item.setAllowNegativeStock(allowNegativeStock);
        item.setMinimumQuantity(minimumQuantity);
        item.setReorderQuantity(reorderQuantity);
        item.setCurrentQuantity(BigDecimal.ZERO);
        item.setAverageCost(BigDecimal.ZERO);
        item.setLastCost(BigDecimal.ZERO);
        item = inventoryItemRepository.save(item);

        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.INVENTORY_ITEM_CREATED,
                OperationalEntityType.INVENTORY_ITEM,
                item.getId(),
                OperationalOrigem.SYSTEM,
                "created",
                Map.of("tenantId", tenant.getId(), "itemId", item.getId(), "type", type != null ? type.name() : null, "at", LocalDateTime.now().toString()),
                null,
                null
        );

        return item;
    }

    @Transactional
    public InventoryItem update(Long tenantId,
                                Long itemId,
                                String name,
                                String sku,
                                InventoryItemType type,
                                String category,
                                String baseUnitCode,
                                Boolean stockControlEnabled,
                                Boolean allowNegativeStock,
                                BigDecimal minimumQuantity,
                                BigDecimal reorderQuantity,
                                InventoryItemStatus status) {
        InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId).orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        if (item.getTenant() == null || !item.getTenant().getId().equals(tenantId)) throw new BusinessException("INVENTORY_FORBIDDEN");
        if (name != null) item.setName(name);
        if (sku != null) item.setSku(sku);
        if (type != null) item.setType(type);
        if (category != null) item.setCategory(category);
        if (baseUnitCode != null) item.setBaseUnit(resolveUnit(tenantId, baseUnitCode));
        if (stockControlEnabled != null) item.setStockControlEnabled(stockControlEnabled);
        if (allowNegativeStock != null) item.setAllowNegativeStock(allowNegativeStock);
        if (minimumQuantity != null) item.setMinimumQuantity(minimumQuantity);
        if (reorderQuantity != null) item.setReorderQuantity(reorderQuantity);
        if (status != null) item.setStatus(status);
        item = inventoryItemRepository.save(item);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_ITEM_UPDATED,
                OperationalEntityType.INVENTORY_ITEM,
                item.getId(),
                OperationalOrigem.SYSTEM,
                "updated",
                Map.of("tenantId", tenantId, "itemId", item.getId(), "at", LocalDateTime.now().toString()),
                null,
                null
        );

        return item;
    }

    private UnitOfMeasure resolveUnit(Long tenantId, String unitCode) {
        if (unitCode == null || unitCode.isBlank()) throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        UnitOfMeasure u = unitOfMeasureRepository.findByTenantIdAndCode(tenantId, unitCode)
                .orElseGet(() -> unitOfMeasureRepository.findByTenantIdAndCode(null, unitCode).orElse(null));
        if (u == null) throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        return u;
    }
}
