package com.restaurante.financeiro.reconciliation.repository;
import com.restaurante.financeiro.reconciliation.model.ReconciliationMaterializationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ReconciliationMaterializationAuditRepository extends JpaRepository<ReconciliationMaterializationAudit,Long>{Optional<ReconciliationMaterializationAudit> findByIdempotencyKey(String key);}
