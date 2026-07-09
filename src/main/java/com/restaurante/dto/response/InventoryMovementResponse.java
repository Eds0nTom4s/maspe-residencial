package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryMovementDirection;
import com.restaurante.model.enums.InventoryMovementReferenceType;
import com.restaurante.model.enums.InventoryMovementSource;
import com.restaurante.model.enums.InventoryMovementType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMovementResponse {
    private Long id;
    private Long inventoryItemId;
    private InventoryMovementType movementType;
    private InventoryMovementDirection direction;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal quantityBaseUnit;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private InventoryMovementReferenceType referenceType;
    private Long referenceId;
    private InventoryMovementSource source;
    private String reason;
    private LocalDateTime createdAt;
}

