package com.restaurante.repository;

import com.restaurante.model.entity.BusinessProvisioningOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BusinessProvisioningOperationRepository extends JpaRepository<BusinessProvisioningOperation, Long> {
    Optional<BusinessProvisioningOperation> findByOperationId(String operationId);
    Optional<BusinessProvisioningOperation> findByBusinessAccountIdAndIdempotencyKey(Long accountId, String key);
}
