package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryRecipeLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryRecipeLineRepository extends JpaRepository<InventoryRecipeLine, Long> {
    List<InventoryRecipeLine> findAllByRecipeIdOrderByIdAsc(Long recipeId);
}

