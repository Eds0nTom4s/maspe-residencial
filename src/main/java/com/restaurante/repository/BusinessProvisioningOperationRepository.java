package com.restaurante.repository;

import com.restaurante.model.entity.BusinessProvisioningOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BusinessProvisioningOperationRepository extends JpaRepository<BusinessProvisioningOperation, Long> {
    Optional<BusinessProvisioningOperation> findByOperationId(String operationId);
    Optional<BusinessProvisioningOperation> findByBusinessAccountIdAndIdempotencyKey(Long accountId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BusinessProvisioningOperation o where o.id = :id")
    Optional<BusinessProvisioningOperation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BusinessProvisioningOperation o where o.operationId = :operationId")
    Optional<BusinessProvisioningOperation> findByOperationIdForUpdate(@Param("operationId") String operationId);
}
