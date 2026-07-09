package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryReturnLineRepository extends JpaRepository<InventoryReturnLine, Long> {
    List<InventoryReturnLine> findAllByReturnRecordIdOrderByIdAsc(Long returnRecordId);

    @Query("""
            select coalesce(sum(l.quantityBaseUnit), 0)
            from InventoryReturnLine l
            where l.tenant.id = :tenantId
              and l.consumptionLine.id = :consumptionLineId
              and l.returnRecord.status = com.restaurante.model.enums.InventoryReturnStatus.PROCESSED
            """)
    java.math.BigDecimal sumProcessedReturnedBaseQtyByConsumptionLine(@Param("tenantId") Long tenantId,
                                                                     @Param("consumptionLineId") Long consumptionLineId);
}

