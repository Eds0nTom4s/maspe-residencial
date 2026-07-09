package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryConsumptionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryConsumptionLineRepository extends JpaRepository<InventoryConsumptionLine, Long> {
    List<InventoryConsumptionLine> findAllByConsumptionRecordIdOrderByIdAsc(Long recordId);

    @Query("""
            select l from InventoryConsumptionLine l
            where l.consumptionRecord.id = :recordId
              and l.pedidoItem.id = :pedidoItemId
            order by l.id asc
            """)
    List<InventoryConsumptionLine> findAllByConsumptionRecordIdAndPedidoItemIdOrderByIdAsc(@Param("recordId") Long recordId,
                                                                                           @Param("pedidoItemId") Long pedidoItemId);
}
