package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.InventoryRecipeRepository;
import com.restaurante.inventory.repository.ProductInventoryMappingRepository;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.InventoryRecipe;
import com.restaurante.model.entity.ProductInventoryMapping;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.ProductStockPolicy;
import com.restaurante.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductInventoryMappingService {

    private final ProductInventoryMappingRepository mappingRepository;
    private final ProdutoRepository produtoRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryRecipeRepository recipeRepository;

    @Transactional(readOnly = true)
    public List<ProductInventoryMapping> list(Long tenantId) {
        return mappingRepository.findAllByTenantIdOrderByIdAsc(tenantId);
    }

    @Transactional
    public ProductInventoryMapping upsert(Tenant tenant, Long productId, Long inventoryItemId, Long recipeId, ProductStockPolicy policy) {
        Produto product = produtoRepository.findById(productId).orElseThrow(() -> new BusinessException("PRODUTO_NOT_FOUND"));
        if (product.getTenant() == null || !product.getTenant().getId().equals(tenant.getId())) throw new BusinessException("INVENTORY_FORBIDDEN");

        InventoryItem item = inventoryItemId != null ? itemRepository.findById(inventoryItemId).orElse(null) : null;
        if (item != null && (item.getTenant() == null || !item.getTenant().getId().equals(tenant.getId()))) throw new BusinessException("INVENTORY_FORBIDDEN");

        InventoryRecipe recipe = recipeId != null ? recipeRepository.findById(recipeId).orElse(null) : null;
        if (recipe != null && (recipe.getTenant() == null || !recipe.getTenant().getId().equals(tenant.getId()))) throw new BusinessException("INVENTORY_FORBIDDEN");

        ProductInventoryMapping m = mappingRepository.findByTenantIdAndProductId(tenant.getId(), productId).orElse(null);
        if (m == null) {
            m = new ProductInventoryMapping();
            m.setTenant(tenant);
            m.setProduct(product);
        }
        m.setInventoryItem(item);
        m.setRecipe(recipe);
        if (policy != null) m.setStockPolicy(policy);
        return mappingRepository.save(m);
    }
}

