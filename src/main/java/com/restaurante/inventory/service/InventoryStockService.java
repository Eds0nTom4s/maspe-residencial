package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.inventory.repository.UnitOfMeasureRepository;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.InventoryMovement;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.InventoryMovementDirection;
import com.restaurante.model.enums.InventoryMovementReferenceType;
import com.restaurante.model.enums.InventoryMovementSource;
import com.restaurante.model.enums.InventoryMovementType;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static com.restaurante.inventory.util.InventoryMath.scale;

@Service
@RequiredArgsConstructor
public class InventoryStockService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final UnitConversionService unitConversionService;
    private final InventoryProperties properties;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public InventoryMovement stockIn(Long tenantId,
                                    Long itemId,
                                    BigDecimal quantity,
                                    String unitCode,
                                    BigDecimal unitCost,
                                    String reason,
                                    String reference) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVENTORY_STOCK_IN_INVALID_QUANTITY");
        }
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVENTORY_STOCK_IN_INVALID_UNIT_COST");
        }

        InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        Tenant tenant = item.getTenant();
        if (tenant == null || !tenant.getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }

        UnitOfMeasure unit = resolveUnit(tenantId, unitCode);

        BigDecimal qtyBase = unitConversionService.convertQuantity(
                tenantId,
                quantity,
                unit,
                item.getBaseUnit(),
                properties.getMath().getCalculationScale(),
                properties.getMath().getRoundingMode()
        );
        qtyBase = scale(qtyBase, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());

        BigDecimal oldQty = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal oldAvgCost = item.getAverageCost() != null ? item.getAverageCost() : BigDecimal.ZERO;
        BigDecimal incomingUnitCost = scale(unitCost, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());

        BigDecimal newQty = oldQty.add(qtyBase);
        BigDecimal newAvgCost;
        if (newQty.compareTo(BigDecimal.ZERO) == 0) {
            newAvgCost = BigDecimal.ZERO;
        } else {
            BigDecimal numerator = oldQty.multiply(oldAvgCost).add(qtyBase.multiply(incomingUnitCost));
            newAvgCost = numerator.divide(newQty, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());
        }

        item.setCurrentQuantity(scale(newQty, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        item.setAverageCost(scale(newAvgCost, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        item.setLastCost(scale(incomingUnitCost, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        inventoryItemRepository.save(item);

        InventoryMovement movement = new InventoryMovement();
        movement.setTenant(tenant);
        movement.setUnidadeAtendimento(item.getUnidadeAtendimento());
        movement.setInventoryItem(item);
        movement.setMovementType(InventoryMovementType.PURCHASE_IN);
        movement.setDirection(InventoryMovementDirection.IN);
        movement.setQuantity(scale(quantity, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        movement.setUnit(unit);
        movement.setQuantityBaseUnit(qtyBase);
        movement.setUnitCost(incomingUnitCost);
        movement.setTotalCost(scale(qtyBase.multiply(incomingUnitCost), properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        movement.setStockBefore(oldQty);
        movement.setStockAfter(item.getCurrentQuantity());
        movement.setAverageCostBefore(oldAvgCost);
        movement.setAverageCostAfter(item.getAverageCost());
        movement.setReferenceType(InventoryMovementReferenceType.PURCHASE_PLACEHOLDER);
        movement.setReferenceId(null);
        movement.setSource(InventoryMovementSource.ADMIN);
        movement.setReason(composeReason(reason, reference));
        movementRepository.save(movement);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_STOCK_IN_CREATED,
                OperationalEntityType.INVENTORY_MOVEMENT,
                movement.getId(),
                OperationalOrigem.SYSTEM,
                "stock-in",
                Map.of(
                        "tenantId", tenantId,
                        "itemId", itemId,
                        "movementId", movement.getId(),
                        "quantity", movement.getQuantity(),
                        "unit", unit.getCode(),
                        "totalCost", movement.getTotalCost(),
                        "at", LocalDateTime.now().toString()
                ),
                null,
                null
        );

        return movement;
    }

    @Transactional
    public InventoryMovement adjust(Long tenantId,
                                   Long itemId,
                                   InventoryMovementDirection direction,
                                   BigDecimal quantity,
                                   String unitCode,
                                   String reason) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVENTORY_ADJUSTMENT_INVALID_QUANTITY");
        }
        if (direction == null) {
            throw new BusinessException("INVENTORY_ADJUSTMENT_INVALID_DIRECTION");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("INVENTORY_ADJUSTMENT_REASON_REQUIRED");
        }

        InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        if (item.getTenant() == null || !item.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        UnitOfMeasure unit = resolveUnit(tenantId, unitCode);

        BigDecimal qtyBase = unitConversionService.convertQuantity(
                tenantId,
                quantity,
                unit,
                item.getBaseUnit(),
                properties.getMath().getCalculationScale(),
                properties.getMath().getRoundingMode()
        );
        qtyBase = scale(qtyBase, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());

        BigDecimal oldQty = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = direction == InventoryMovementDirection.IN ? oldQty.add(qtyBase) : oldQty.subtract(qtyBase);

        if (!Boolean.TRUE.equals(item.getAllowNegativeStock()) && newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVENTORY_NEGATIVE_STOCK_NOT_ALLOWED");
        }

        item.setCurrentQuantity(scale(newQty, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        inventoryItemRepository.save(item);

        InventoryMovement movement = new InventoryMovement();
        movement.setTenant(item.getTenant());
        movement.setUnidadeAtendimento(item.getUnidadeAtendimento());
        movement.setInventoryItem(item);
        movement.setMovementType(direction == InventoryMovementDirection.IN ? InventoryMovementType.ADJUSTMENT_IN : InventoryMovementType.ADJUSTMENT_OUT);
        movement.setDirection(direction);
        movement.setQuantity(scale(quantity, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        movement.setUnit(unit);
        movement.setQuantityBaseUnit(qtyBase);
        movement.setUnitCost(BigDecimal.ZERO);
        movement.setTotalCost(BigDecimal.ZERO);
        movement.setStockBefore(oldQty);
        movement.setStockAfter(item.getCurrentQuantity());
        movement.setAverageCostBefore(item.getAverageCost());
        movement.setAverageCostAfter(item.getAverageCost());
        movement.setReferenceType(InventoryMovementReferenceType.INVENTORY_ADJUSTMENT);
        movement.setReferenceId(null);
        movement.setSource(InventoryMovementSource.ADMIN);
        movement.setReason(reason);
        movementRepository.save(movement);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_ADJUSTMENT_CREATED,
                OperationalEntityType.INVENTORY_MOVEMENT,
                movement.getId(),
                OperationalOrigem.SYSTEM,
                "adjustment",
                Map.of(
                        "tenantId", tenantId,
                        "itemId", itemId,
                        "movementId", movement.getId(),
                        "direction", direction.name(),
                        "quantity", movement.getQuantity(),
                        "unit", unit.getCode(),
                        "at", LocalDateTime.now().toString()
                ),
                null,
                null
        );

        return movement;
    }

    @Transactional
    public InventoryMovement waste(Long tenantId,
                                  Long itemId,
                                  BigDecimal quantity,
                                  String unitCode,
                                  String reason) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVENTORY_WASTE_INVALID_QUANTITY");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("INVENTORY_WASTE_REASON_REQUIRED");
        }

        InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        if (item.getTenant() == null || !item.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        UnitOfMeasure unit = resolveUnit(tenantId, unitCode);

        BigDecimal qtyBase = unitConversionService.convertQuantity(
                tenantId,
                quantity,
                unit,
                item.getBaseUnit(),
                properties.getMath().getCalculationScale(),
                properties.getMath().getRoundingMode()
        );
        qtyBase = scale(qtyBase, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());

        BigDecimal oldQty = item.getCurrentQuantity() != null ? item.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = oldQty.subtract(qtyBase);
        if (!Boolean.TRUE.equals(item.getAllowNegativeStock()) && newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVENTORY_NEGATIVE_STOCK_NOT_ALLOWED");
        }

        item.setCurrentQuantity(scale(newQty, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        inventoryItemRepository.save(item);

        BigDecimal unitCost = item.getAverageCost() != null ? item.getAverageCost() : BigDecimal.ZERO;
        InventoryMovement movement = new InventoryMovement();
        movement.setTenant(item.getTenant());
        movement.setUnidadeAtendimento(item.getUnidadeAtendimento());
        movement.setInventoryItem(item);
        movement.setMovementType(InventoryMovementType.WASTE);
        movement.setDirection(InventoryMovementDirection.OUT);
        movement.setQuantity(scale(quantity, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        movement.setUnit(unit);
        movement.setQuantityBaseUnit(qtyBase);
        movement.setUnitCost(unitCost);
        movement.setTotalCost(scale(qtyBase.multiply(unitCost), properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        movement.setStockBefore(oldQty);
        movement.setStockAfter(item.getCurrentQuantity());
        movement.setAverageCostBefore(item.getAverageCost());
        movement.setAverageCostAfter(item.getAverageCost());
        movement.setReferenceType(InventoryMovementReferenceType.SYSTEM);
        movement.setReferenceId(null);
        movement.setSource(InventoryMovementSource.ADMIN);
        movement.setReason(reason);
        movementRepository.save(movement);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_WASTE_CREATED,
                OperationalEntityType.INVENTORY_MOVEMENT,
                movement.getId(),
                OperationalOrigem.SYSTEM,
                "waste",
                Map.of(
                        "tenantId", tenantId,
                        "itemId", itemId,
                        "movementId", movement.getId(),
                        "quantity", movement.getQuantity(),
                        "unit", unit.getCode(),
                        "totalCost", movement.getTotalCost(),
                        "at", LocalDateTime.now().toString()
                ),
                null,
                null
        );

        return movement;
    }

    private UnitOfMeasure resolveUnit(Long tenantId, String unitCode) {
        if (unitCode == null || unitCode.isBlank()) {
            throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        }
        return unitOfMeasureRepository.findByTenantIdAndCode(tenantId, unitCode)
                .orElseGet(() -> unitOfMeasureRepository.findByTenantIdAndCode(null, unitCode).orElse(null));
    }

    private String composeReason(String reason, String reference) {
        String r = reason != null ? reason.trim() : "";
        String ref = reference != null ? reference.trim() : "";
        if (!r.isBlank() && !ref.isBlank()) return r + " | ref=" + ref;
        if (!ref.isBlank()) return "ref=" + ref;
        return r;
    }
}
