package com.restaurante.repository;

import com.restaurante.model.entity.PublicQrPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublicQrPaymentRequestRepository extends JpaRepository<PublicQrPaymentRequest, Long> {

    Optional<PublicQrPaymentRequest> findByTenantIdAndPedidoIdAndIdempotencyKey(Long tenantId, Long pedidoId, String idempotencyKey);
}

