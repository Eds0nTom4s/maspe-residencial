package com.restaurante.financeiro.repository;

import com.restaurante.model.entity.PagamentoCallbackLog;
import com.restaurante.model.enums.CallbackProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PagamentoCallbackLogRepository extends JpaRepository<PagamentoCallbackLog, Long> {

    Page<PagamentoCallbackLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<PagamentoCallbackLog> findByTenantIdAndProcessingStatus(Long tenantId, CallbackProcessingStatus status, Pageable pageable);

    Page<PagamentoCallbackLog> findByProcessingStatus(CallbackProcessingStatus status, Pageable pageable);

    Page<PagamentoCallbackLog> findByPagamentoId(Long pagamentoId, Pageable pageable);

    Page<PagamentoCallbackLog> findByTenantIdIsNull(Pageable pageable);

    long countByTenantIdAndProcessingStatusAndReceivedAtBetween(Long tenantId, CallbackProcessingStatus status, LocalDateTime start, LocalDateTime end);

    long countByProcessingStatusAndReceivedAtBetween(CallbackProcessingStatus status, LocalDateTime start, LocalDateTime end);

    Optional<PagamentoCallbackLog> findFirstByTenantIdOrderByReceivedAtDesc(Long tenantId);

    Optional<PagamentoCallbackLog> findFirstByOrderByReceivedAtDesc();

    Optional<PagamentoCallbackLog> findFirstByPagamentoIdOrderByReceivedAtDesc(Long pagamentoId);
}
