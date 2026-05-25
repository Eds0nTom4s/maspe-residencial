package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryConsumptionLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryConsumptionLineRepository extends JpaRepository<InventoryConsumptionLine, Long> {
    List<InventoryConsumptionLine> findAllByConsumptionRecordIdOrderByIdAsc(Long recordId);
}

