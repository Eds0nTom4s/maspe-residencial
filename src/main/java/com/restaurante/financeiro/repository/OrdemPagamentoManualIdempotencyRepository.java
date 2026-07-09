package com.restaurante.financeiro.repository;

import com.restaurante.model.entity.OrdemPagamentoManualIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrdemPagamentoManualIdempotencyRepository extends JpaRepository<OrdemPagamentoManualIdempotencyRecord, Long> {

    Optional<OrdemPagamentoManualIdempotencyRecord> findByTenantIdAndDispositivoIdAndIdempotencyKey(Long tenantId, Long dispositivoId, String idempotencyKey);

    Optional<OrdemPagamentoManualIdempotencyRecord> findByTenantIdAndDispositivoIdAndClientRequestId(Long tenantId, Long dispositivoId, String clientRequestId);
}

