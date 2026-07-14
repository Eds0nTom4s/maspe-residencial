package com.restaurante.financeiro.reconciliation.repository;

import com.restaurante.financeiro.reconciliation.model.PagamentoReconciliationCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PagamentoReconciliationCaseRepository extends JpaRepository<PagamentoReconciliationCase,Long>, JpaSpecificationExecutor<PagamentoReconciliationCase> {
    Optional<PagamentoReconciliationCase> findByPagamentoIdAndActiveTrue(Long pagamentoId);
    Optional<PagamentoReconciliationCase> findByIdAndTenantId(Long id, Long tenantId);
    @Query("select c from PagamentoReconciliationCase c where c.id=:id and c.tenant.id=:tenantId")
    Optional<PagamentoReconciliationCase> findTenantCase(@Param("id") Long id, @Param("tenantId") Long tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PagamentoReconciliationCase c where c.id=:id and c.tenant.id=:tenantId")
    Optional<PagamentoReconciliationCase> findTenantCaseForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    long countByPagamentoIdAndActiveTrue(Long pagamentoId);
}
