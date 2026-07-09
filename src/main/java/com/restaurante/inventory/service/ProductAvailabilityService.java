package com.restaurante.inventory.service;

import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.inventory.repository.InventoryRecipeLineRepository;
import com.restaurante.inventory.repository.InventoryRecipeRepository;
import com.restaurante.inventory.repository.ProductInventoryMappingRepository;
import com.restaurante.model.entity.InventoryRecipe;
import com.restaurante.model.entity.InventoryRecipeLine;
import com.restaurante.model.entity.ProductInventoryMapping;
import com.restaurante.model.enums.InventoryRecipeStatus;
import com.restaurante.model.enums.ProductStockPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAvailabilityService {

    private final ProductInventoryMappingRepository mappingRepository;
    private final InventoryRecipeRepository recipeRepository;
    private final InventoryRecipeLineRepository recipeLineRepository;
    private final InventoryProperties properties;

    @Transactional(readOnly = true)
    public AvailabilityResult availabilityForProduct(Long tenantId, Long productId) {
        ProductInventoryMapping mapping = mappingRepository.findByTenantIdAndProductId(tenantId, productId).orElse(null);
        if (mapping == null || mapping.getStockPolicy() == null || mapping.getStockPolicy() == ProductStockPolicy.NO_STOCK_CONTROL) {
            return new AvailabilityResult(true, null, "NO_STOCK_CONTROL");
        }
        if (mapping.getStockPolicy() == ProductStockPolicy.MANUAL_ONLY) {
            return new AvailabilityResult(true, null, "MANUAL_ONLY");
        }
        if (mapping.getStockPolicy() == ProductStockPolicy.DIRECT_ITEM_DEDUCTION) {
            if (mapping.getInventoryItem() == null) {
                return new AvailabilityResult(true, null, "MISSING_INVENTORY_ITEM");
            }
            BigDecimal qty = mapping.getInventoryItem().getCurrentQuantity();
            boolean available = qty == null || qty.compareTo(BigDecimal.ONE) >= 0;
            return new AvailabilityResult(available, qty, "DIRECT_ITEM_DEDUCTION");
        }

        InventoryRecipe recipe = mapping.getRecipe();
        if (recipe == null) {
            List<InventoryRecipe> effective = recipeRepository.findEffectiveByProduct(tenantId, productId, InventoryRecipeStatus.ACTIVE, LocalDateTime.now());
            recipe = effective.isEmpty() ? null : effective.get(0);
        }
        if (recipe == null || recipe.getStatus() != InventoryRecipeStatus.ACTIVE) {
            return new AvailabilityResult(true, null, "MISSING_RECIPE");
        }

        List<InventoryRecipeLine> lines = recipeLineRepository.findAllByRecipeIdOrderByIdAsc(recipe.getId());
        BigDecimal min = null;
        for (InventoryRecipeLine line : lines) {
            if (line.getInventoryItem() == null || line.getQuantity() == null) continue;
            BigDecimal perUnit = line.getQuantity().divide(recipe.getYieldQuantity(), properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());
            BigDecimal current = line.getInventoryItem().getCurrentQuantity() != null ? line.getInventoryItem().getCurrentQuantity() : BigDecimal.ZERO;
            if (perUnit.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal possible = current.divide(perUnit, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());
            min = (min == null) ? possible : min.min(possible);
        }
        boolean available = min == null || min.compareTo(BigDecimal.ONE) >= 0;
        return new AvailabilityResult(available, min, "RECIPE_DEDUCTION");
    }

    public record AvailabilityResult(boolean available, BigDecimal estimatedAvailableQuantity, String stockPolicy) {}
}

