package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.InventoryRecipeLineRepository;
import com.restaurante.inventory.repository.InventoryRecipeRepository;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.UnitOfMeasureRepository;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.InventoryRecipe;
import com.restaurante.model.entity.InventoryRecipeLine;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.InventoryRecipeStatus;
import com.restaurante.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryRecipeService {

    private final InventoryRecipeRepository recipeRepository;
    private final InventoryRecipeLineRepository lineRepository;
    private final ProdutoRepository produtoRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;

    @Transactional
    public InventoryRecipe createRecipe(Tenant tenant, Long productId, String name, BigDecimal yieldQty, String yieldUnitCode) {
        Produto product = produtoRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUTO_NOT_FOUND"));
        if (product.getTenant() == null || !product.getTenant().getId().equals(tenant.getId())) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        UnitOfMeasure yieldUnit = resolveUnit(tenant.getId(), yieldUnitCode);
        InventoryRecipe recipe = new InventoryRecipe();
        recipe.setTenant(tenant);
        recipe.setProduct(product);
        recipe.setName(name);
        recipe.setStatus(InventoryRecipeStatus.DRAFT);
        recipe.setYieldQuantity(yieldQty != null ? yieldQty : BigDecimal.ONE);
        recipe.setYieldUnit(yieldUnit);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public InventoryRecipeLine addLine(Tenant tenant,
                                       Long recipeId,
                                       Long inventoryItemId,
                                       BigDecimal quantity,
                                       String unitCode,
                                       BigDecimal wastePercentage) {
        InventoryRecipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RECIPE_NOT_FOUND"));
        if (recipe.getTenant() == null || !recipe.getTenant().getId().equals(tenant.getId())) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
        if (item.getTenant() == null || !item.getTenant().getId().equals(tenant.getId())) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        UnitOfMeasure unit = resolveUnit(tenant.getId(), unitCode);
        InventoryRecipeLine line = new InventoryRecipeLine();
        line.setRecipe(recipe);
        line.setTenant(tenant);
        line.setInventoryItem(item);
        line.setQuantity(quantity);
        line.setUnit(unit);
        line.setWastePercentage(wastePercentage != null ? wastePercentage : BigDecimal.ZERO);
        line.setCostSnapshot(item.getAverageCost());
        return lineRepository.save(line);
    }

    @Transactional(readOnly = true)
    public List<InventoryRecipeLine> listLines(Long tenantId, Long recipeId) {
        InventoryRecipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RECIPE_NOT_FOUND"));
        if (recipe.getTenant() == null || !recipe.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        return lineRepository.findAllByRecipeIdOrderByIdAsc(recipeId);
    }

    @Transactional
    public InventoryRecipe activate(Long tenantId, Long recipeId) {
        InventoryRecipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RECIPE_NOT_FOUND"));
        if (recipe.getTenant() == null || !recipe.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        recipe.setStatus(InventoryRecipeStatus.ACTIVE);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public InventoryRecipe archive(Long tenantId, Long recipeId) {
        InventoryRecipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RECIPE_NOT_FOUND"));
        if (recipe.getTenant() == null || !recipe.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }
        recipe.setStatus(InventoryRecipeStatus.ARCHIVED);
        return recipeRepository.save(recipe);
    }

    private UnitOfMeasure resolveUnit(Long tenantId, String unitCode) {
        if (unitCode == null || unitCode.isBlank()) throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        return unitOfMeasureRepository.findByTenantIdAndCode(tenantId, unitCode)
                .orElseGet(() -> unitOfMeasureRepository.findByTenantIdAndCode(null, unitCode).orElseThrow(() -> new BusinessException("INVENTORY_UNIT_NOT_FOUND")));
    }
}

