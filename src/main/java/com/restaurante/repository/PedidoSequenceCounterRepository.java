package com.restaurante.repository;

import com.restaurante.model.entity.PedidoSequenceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PedidoSequenceCounterRepository extends JpaRepository<PedidoSequenceCounter, Long> {

    Optional<PedidoSequenceCounter> findByTenantIdAndDataReferencia(Long tenantId, LocalDate dataReferencia);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PedidoSequenceCounter> findForUpdateByTenantIdAndDataReferencia(Long tenantId, LocalDate dataReferencia);
}

