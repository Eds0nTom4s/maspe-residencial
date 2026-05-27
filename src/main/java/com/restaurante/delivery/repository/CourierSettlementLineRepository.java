package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierSettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourierSettlementLineRepository extends JpaRepository<CourierSettlementLine, Long> {
    List<CourierSettlementLine> findByBatchId(Long batchId);
}
