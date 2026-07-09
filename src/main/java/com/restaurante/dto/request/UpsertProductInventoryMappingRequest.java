package com.restaurante.dto.request;

import com.restaurante.model.enums.ProductStockPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertProductInventoryMappingRequest {
    @NotNull
    private Long productId;
    private Long inventoryItemId;
    private Long recipeId;
    @NotNull
    private ProductStockPolicy stockPolicy;
}

