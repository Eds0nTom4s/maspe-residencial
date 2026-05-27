package com.restaurante.delivery.repository;

import com.restaurante.model.entity.CourierSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierSettlementBatchRepository extends JpaRepository<CourierSettlementBatch, Long> {
    Optional<CourierSettlementBatch> findByBatchNumber(String batchNumber);
}
