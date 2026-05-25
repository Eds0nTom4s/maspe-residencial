package com.restaurante.dto.request;

import com.restaurante.model.enums.InventoryItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryItemRequest {
    @NotBlank
    private String name;
    private String sku;
    @NotNull
    private InventoryItemType type;
    private String category;
    @NotBlank
    private String baseUnitCode;
    private Boolean stockControlEnabled;
    private Boolean allowNegativeStock;
    private BigDecimal minimumQuantity;
    private BigDecimal reorderQuantity;
}

