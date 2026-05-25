package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingInvoiceSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TenantBillingInvoiceSequenceRepository extends JpaRepository<TenantBillingInvoiceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from TenantBillingInvoiceSequence s
            where s.tenant.id = :tenantId
              and s.year = :year
            """)
    Optional<TenantBillingInvoiceSequence> findKeyForUpdate(@Param("tenantId") Long tenantId, @Param("year") Integer year);
}

