package com.restaurante.repository;

import com.restaurante.model.entity.PublicQrOrderRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublicQrOrderRequestRepository extends JpaRepository<PublicQrOrderRequest, Long> {

    Optional<PublicQrOrderRequest> findByTenantIdAndQrCodeOperacionalIdAndIdempotencyKey(
            Long tenantId,
            Long qrCodeOperacionalId,
            String idempotencyKey
    );
}

