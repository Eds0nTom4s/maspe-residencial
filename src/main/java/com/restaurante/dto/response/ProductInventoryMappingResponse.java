package com.restaurante.dto.response;

import com.restaurante.model.enums.ProductStockPolicy;
import lombok.Data;

@Data
public class ProductInventoryMappingResponse {
    private Long id;
    private Long productId;
    private Long inventoryItemId;
    private Long recipeId;
    private ProductStockPolicy stockPolicy;
    private String status;
}

