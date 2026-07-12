package com.restaurante.financeiro.reconciliation.repository;
import com.restaurante.financeiro.reconciliation.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PagamentoReconciliationCaseEventRepository extends JpaRepository<PagamentoReconciliationCaseEvent,Long> {
    List<PagamentoReconciliationCaseEvent> findByReconciliationCaseIdOrderByCreatedAtAsc(Long caseId);
    Optional<PagamentoReconciliationCaseEvent> findByReconciliationCaseIdAndActionAndIdempotencyKey(Long caseId, ReconciliationCaseAction action, String key);
}
