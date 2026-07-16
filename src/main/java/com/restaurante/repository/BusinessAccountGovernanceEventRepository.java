package com.restaurante.repository;

import com.restaurante.model.entity.BusinessAccountGovernanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BusinessAccountGovernanceEventRepository extends JpaRepository<BusinessAccountGovernanceEvent, Long> {
    Optional<BusinessAccountGovernanceEvent> findByScopeKeyAndActionAndIdempotencyKey(
            String scopeKey, String action, String idempotencyKey);
}
